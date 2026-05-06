package com.lulybi.athena;

import com.lulybi.core.mapping.Mapper;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import software.amazon.awssdk.services.athena.AthenaAsyncClient;
import software.amazon.awssdk.services.athena.AthenaAsyncClientBuilder;
import software.amazon.awssdk.services.athena.model.*;

public class AsyncAthenaService extends AbstractAthenaService implements AutoCloseable {
  private final AthenaAsyncClient athenaAsyncClient;
  private final ScheduledExecutorService scheduler;

  private AsyncAthenaService(Builder builder) {
    super(builder.workGroup, builder.queryTimeout, builder.pollingInterval);
    AthenaAsyncClientBuilder sdkBuilder = AthenaAsyncClient.builder();
    if (builder.region != null) sdkBuilder.region(builder.region);
    this.athenaAsyncClient = sdkBuilder.build();
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r);
              t.setDaemon(true);
              return t;
            });
  }

  public <T> CompletableFuture<QueryResult<List<T>>> execute(
      Class<T> recordClass, Consumer<AthenaQuery<T>> queryDef) {
    return stream(recordClass, queryDef)
        .thenApply(
            res ->
                new QueryResult<>(
                    res.results().toList(), res.queryExecutionId(), res.statistics()));
  }

  public <T> CompletableFuture<QueryResult<Stream<T>>> stream(
      Class<T> recordClass, Consumer<AthenaQuery<T>> queryDef) {
    AthenaQuery<T> builder = new AthenaQuery<>(getMapperOrThrow(recordClass));
    queryDef.accept(builder);

    CompletableFuture<QueryResult<Stream<T>>> finalFuture = new CompletableFuture<>();

    withRetryAsync(
            () ->
                athenaAsyncClient.startQueryExecution(
                    r -> {
                      r.queryString(builder.build())
                          .queryExecutionContext(
                              q -> q.database(builder.getMapper().getDatabaseName()));
                      if (workGroup != null) r.workGroup(workGroup);
                    }),
            scheduler)
        .thenCompose(
            startResponse -> {
              finalFuture.whenComplete(
                  (res, ex) -> {
                    if (finalFuture.isCancelled()) {
                      athenaAsyncClient.stopQueryExecution(
                          r -> r.queryExecutionId(startResponse.queryExecutionId()));
                    }
                  });

              return waitForQueryToComplete(startResponse.queryExecutionId())
                  .thenApply(
                      finalStatus -> {
                        Stream<T> lazyStream =
                            StreamSupport.stream(
                                new AthenaQueryResultSpliterator<>(
                                    athenaAsyncClient,
                                    startResponse.queryExecutionId(),
                                    builder.getMapper(),
                                    scheduler),
                                false); // Not parallel

                        return new QueryResult<>(
                            lazyStream,
                            startResponse.queryExecutionId(),
                            finalStatus.queryExecution().statistics());
                      });
            })
        .whenComplete(
            (res, ex) -> {
              if (ex != null) finalFuture.completeExceptionally(ex);
              else finalFuture.complete(res);
            });

    return finalFuture;
  }

  public <T> CompletableFuture<QueryResult<Void>> forEach(
      Class<T> recordClass, Consumer<AthenaQuery<T>> queryDef, Consumer<T> action) {
    AthenaQuery<T> builder = new AthenaQuery<>(getMapperOrThrow(recordClass));
    queryDef.accept(builder);

    CompletableFuture<QueryResult<Void>> finalFuture = new CompletableFuture<>();

    withRetryAsync(
            () ->
                athenaAsyncClient.startQueryExecution(
                    r -> {
                      r.queryString(builder.build())
                          .queryExecutionContext(
                              q -> q.database(builder.getMapper().getDatabaseName()));
                      if (workGroup != null) r.workGroup(workGroup);
                    }),
            scheduler)
        .thenCompose(
            startResponse -> {
              finalFuture.whenComplete(
                  (res, ex) -> {
                    if (finalFuture.isCancelled()) {
                      athenaAsyncClient.stopQueryExecution(
                          r -> r.queryExecutionId(startResponse.queryExecutionId()));
                    }
                  });

              return waitForQueryToComplete(startResponse.queryExecutionId())
                  .thenCompose(
                      finalStatus ->
                          processPagesSequentially(
                                  startResponse.queryExecutionId(), null, action, builder, true)
                              .thenApply(
                                  v ->
                                      new QueryResult<Void>(
                                          null,
                                          startResponse.queryExecutionId(),
                                          finalStatus.queryExecution().statistics())));
            })
        .whenComplete(
            (res, ex) -> {
              if (ex != null) finalFuture.completeExceptionally(ex);
              else finalFuture.complete(res);
            });

    return finalFuture;
  }

  public <T> CompletableFuture<Void> createTable(
      Class<T> recordClass, Consumer<AthenaTable<T>> tableDef) {
    AthenaTable<T> builder = new AthenaTable<>(getMapperOrThrow(recordClass));
    tableDef.accept(builder);

    return withRetryAsync(
            () ->
                athenaAsyncClient.startQueryExecution(
                    r -> {
                      r.queryString(builder.build())
                          .queryExecutionContext(
                              q -> q.database(builder.getMapper().getDatabaseName()));
                      if (workGroup != null) r.workGroup(workGroup);
                    }),
            scheduler)
        .thenCompose(response -> waitForQueryToComplete(response.queryExecutionId()))
        .thenCompose(
            status -> {
              if (builder.shouldRepairPartitions()) {
                String repairQuery =
                    String.format(
                        "MSCK REPAIR TABLE %s.%s",
                        builder.getMapper().getDatabaseName(), builder.getMapper().getTableName());
                return withRetryAsync(
                        () ->
                            athenaAsyncClient.startQueryExecution(
                                r -> {
                                  r.queryString(repairQuery);
                                  if (workGroup != null) r.workGroup(workGroup);
                                }),
                        scheduler)
                    .thenCompose(resp -> waitForQueryToComplete(resp.queryExecutionId()))
                    .thenApply(rid -> null);
              }
              return CompletableFuture.completedFuture(null);
            });
  }

  public <T> CompletableFuture<Void> addPartitions(
      Class<T> recordClass, Consumer<AthenaPartition<T>> partitionDef) {
    AthenaPartition<T> builder = new AthenaPartition<>(getMapperOrThrow(recordClass));
    partitionDef.accept(builder);

    return withRetryAsync(
            () ->
                athenaAsyncClient.startQueryExecution(
                    r -> {
                      r.queryString(builder.build());
                      if (workGroup != null) r.workGroup(workGroup);
                    }),
            scheduler)
        .thenCompose(response -> waitForQueryToComplete(response.queryExecutionId()))
        .thenApply(rid -> null);
  }

  private CompletableFuture<GetQueryExecutionResponse> waitForQueryToComplete(String executionId) {
    CompletableFuture<GetQueryExecutionResponse> completionFuture = new CompletableFuture<>();
    Instant deadline = Instant.now().plus(queryTimeout);
    checkStatus(executionId, deadline, completionFuture);
    return completionFuture;
  }

  private void checkStatus(
      String executionId,
      Instant deadline,
      CompletableFuture<GetQueryExecutionResponse> completionFuture) {
    if (Instant.now().isAfter(deadline)) {
      completionFuture.completeExceptionally(
          new RuntimeException(
              String.format(
                  "Query %s timed out after %d seconds", executionId, queryTimeout.toSeconds())));
      return;
    }

    withRetryAsync(
            () -> athenaAsyncClient.getQueryExecution(r -> r.queryExecutionId(executionId)),
            scheduler)
        .thenAccept(
            response -> {
              QueryExecutionState state = response.queryExecution().status().state();
              if (state == QueryExecutionState.SUCCEEDED) {
                completionFuture.complete(response);
              } else if (state == QueryExecutionState.FAILED
                  || state == QueryExecutionState.CANCELLED) {
                completionFuture.completeExceptionally(
                    new RuntimeException(
                        String.format(
                            "Query %s %s: %s",
                            executionId,
                            state,
                            response.queryExecution().status().stateChangeReason())));
              } else {
                scheduler.schedule(
                    () -> checkStatus(executionId, deadline, completionFuture),
                    pollingInterval.toMillis(),
                    TimeUnit.MILLISECONDS);
              }
            })
        .exceptionally(
            ex -> {
              completionFuture.completeExceptionally(ex);
              return null;
            });
  }

  // Make withRetryAsync public static
  public static <U> CompletableFuture<U> withRetryAsync(
      java.util.function.Supplier<CompletableFuture<U>> action,
      ScheduledExecutorService scheduler) {
    return AbstractAthenaService.withRetryAsync(action, scheduler);
  }

  // Make mapResults public static
  public static <T> List<T> mapResults(
      GetQueryResultsResponse response, Mapper<T> mapper, boolean isFirstPage) {
    return AbstractAthenaService.mapResults(response, mapper, isFirstPage);
  }

  private <T> CompletableFuture<Void> processPagesSequentially(
      String executionId,
      String nextToken,
      Consumer<T> action,
      AthenaQuery<T> builder,
      boolean isFirstPage) {
    return withRetryAsync(
            () ->
                athenaAsyncClient.getQueryResults(
                    r -> r.queryExecutionId(executionId).nextToken(nextToken)),
            scheduler)
        .thenCompose(
            response -> {
              mapResults(response, builder.getMapper(), isFirstPage).forEach(action);
              if (response.nextToken() != null)
                return processPagesSequentially(
                    executionId, response.nextToken(), action, builder, false);
              return CompletableFuture.completedFuture(null);
            });
  }

  @Override
  public void close() {
    athenaAsyncClient.close();
    scheduler.shutdown();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends AbstractAthenaService.Builder<Builder, AsyncAthenaService> {
    @Override
    public AsyncAthenaService build() {
      return new AsyncAthenaService(this);
    }
  }
}
