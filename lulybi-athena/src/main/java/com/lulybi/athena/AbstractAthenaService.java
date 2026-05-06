package com.lulybi.athena;

import com.lulybi.core.mapping.Mapper;
import com.lulybi.core.registry.MapperRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.model.AthenaException;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.Row;

public abstract class AbstractAthenaService {
  private static final int MAX_RETRIES = 5;
  private static final long INITIAL_BACKOFF_MS = 100;

  protected final String workGroup;
  protected final Duration queryTimeout;
  protected final Duration pollingInterval;

  protected AbstractAthenaService(
      String workGroup, Duration queryTimeout, Duration pollingInterval) {
    this.workGroup = workGroup;
    this.queryTimeout = queryTimeout != null ? queryTimeout : Duration.ofMinutes(30);
    this.pollingInterval = pollingInterval != null ? pollingInterval : Duration.ofSeconds(1);
  }

  protected <T> T withRetry(Supplier<T> action) {
    for (int retries = 0; ; retries++) {
      try {
        return action.get();
      } catch (AthenaException e) {
        if (isThrottlingException(e) && retries < MAX_RETRIES) {
          long backoff = (long) (INITIAL_BACKOFF_MS * Math.pow(2, retries));
          try {
            TimeUnit.MILLISECONDS.sleep(backoff);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw e;
          }
        } else {
          throw e;
        }
      }
    }
  }

  protected static <T> CompletableFuture<T> withRetryAsync(
      Supplier<CompletableFuture<T>> action, ScheduledExecutorService scheduler) {
    return withRetryAsyncInternal(action, scheduler, 0);
  }

  private static <T> CompletableFuture<T> withRetryAsyncInternal(
      Supplier<CompletableFuture<T>> action, ScheduledExecutorService scheduler, int retries) {
    return action
        .get()
        .handle(
            (result, ex) -> {
              if (ex == null) return CompletableFuture.completedFuture(result);

              Throwable cause =
                  ex instanceof java.util.concurrent.CompletionException ? ex.getCause() : ex;
              if (cause instanceof AthenaException ae
                  && isThrottlingException(ae)
                  && retries < MAX_RETRIES) {
                long backoff = (long) (INITIAL_BACKOFF_MS * Math.pow(2, retries));
                CompletableFuture<T> nextTry = new CompletableFuture<>();
                scheduler.schedule(
                    () ->
                        withRetryAsyncInternal(action, scheduler, retries + 1)
                            .thenAccept(nextTry::complete)
                            .exceptionally(
                                innerEx -> {
                                  nextTry.completeExceptionally(innerEx);
                                  return null;
                                }),
                    backoff,
                    TimeUnit.MILLISECONDS);
                return nextTry;
              }
              CompletableFuture<T> failed = new CompletableFuture<>();
              failed.completeExceptionally(ex);
              return failed;
            })
        .thenCompose(f -> f);
  }

  private static boolean isThrottlingException(AthenaException e) {
    int statusCode = e.statusCode();
    return statusCode == 429 || statusCode == 503 || e.isThrottlingException();
  }

  protected <T> Mapper<T> getMapperOrThrow(Class<T> recordClass) {
    Mapper<T> mapper = MapperRegistry.getMapper(recordClass);
    if (mapper == null) {
      throw new IllegalStateException(
          "No mapper found for " + recordClass.getName() + ". Ensure lulybi-processor is running.");
    }
    return mapper;
  }

  protected static <T> List<T> mapResults(
      GetQueryResultsResponse results, Mapper<T> mapper, boolean isFirstPage) {
    if (results == null || results.resultSet() == null) {
      return Collections.emptyList();
    }

    List<ColumnInfo> columnInfo = results.resultSet().resultSetMetadata().columnInfo();
    List<Row> rows = results.resultSet().rows();

    if (rows == null || rows.isEmpty()) {
      return Collections.emptyList();
    }

    int skipCount = (isFirstPage && isHeaderRow(results)) ? 1 : 0;

    if (rows.size() <= skipCount) {
      return Collections.emptyList();
    }

    List<T> mapped = new ArrayList<>(rows.size() - skipCount);
    for (int i = skipCount; i < rows.size(); i++) {
      mapped.add(mapper.map(convertRowToMap(columnInfo, rows.get(i))));
    }
    return mapped;
  }

  private static boolean isHeaderRow(GetQueryResultsResponse results) {
    List<ColumnInfo> columnInfo = results.resultSet().resultSetMetadata().columnInfo();
    List<Row> rows = results.resultSet().rows();
    if (rows.isEmpty()) return false;

    List<Datum> data = rows.get(0).data();
    // Safety check: Athena should return data matching column info count
    if (data == null || data.size() < columnInfo.size()) return false;

    for (int i = 0; i < columnInfo.size(); i++) {
      String label = columnInfo.get(i).name();
      String value = data.get(i).varCharValue();
      // Case-insensitive check is safer for headers
      if (!label.equalsIgnoreCase(value)) return false;
    }
    return true;
  }

  private static Map<String, String> convertRowToMap(List<ColumnInfo> columnInfo, Row row) {
    List<Datum> data = row.data();
    Map<String, String> rowMap = new HashMap<>(columnInfo.size());
    for (int i = 0; i < columnInfo.size(); i++) {
      String value = (data != null && i < data.size()) ? data.get(i).varCharValue() : null;
      rowMap.put(columnInfo.get(i).name(), value);
    }
    return rowMap;
  }

  public abstract static class Builder<B extends Builder<B, C>, C> {
    protected Region region;
    protected String workGroup;
    protected Duration queryTimeout;
    protected Duration pollingInterval;

    @SuppressWarnings("unchecked")
    public B region(Region region) {
      this.region = region;
      return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B region(String region) {
      this.region = Region.of(region);
      return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B workGroup(String workGroup) {
      this.workGroup = workGroup;
      return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B queryTimeout(Duration queryTimeout) {
      this.queryTimeout = queryTimeout;
      return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B pollingInterval(Duration pollingInterval) {
      this.pollingInterval = pollingInterval;
      return (B) this;
    }

    public abstract C build();
  }
}
