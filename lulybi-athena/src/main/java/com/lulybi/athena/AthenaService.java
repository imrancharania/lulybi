package com.lulybi.athena;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.AthenaClientBuilder;
import software.amazon.awssdk.services.athena.model.*;

public class AthenaService extends AbstractAthenaService implements AutoCloseable {
  private final AthenaClient athenaClient;

  private AthenaService(Builder builder) {
    super(builder.workGroup, builder.queryTimeout, builder.pollingInterval);
    AthenaClientBuilder sdkBuilder = AthenaClient.builder();
    if (builder.region != null) sdkBuilder.region(builder.region);
    this.athenaClient = sdkBuilder.build();
  }

  public <T> QueryResult<List<T>> execute(Class<T> recordClass, Consumer<AthenaQuery<T>> queryDef) {
    QueryResult<Stream<T>> result = stream(recordClass, queryDef);
    return new QueryResult<>(
        result.results().toList(), result.queryExecutionId(), result.statistics());
  }

  public <T> QueryResult<Stream<T>> stream(
      Class<T> recordClass, Consumer<AthenaQuery<T>> queryDef) {
    AthenaQuery<T> builder = new AthenaQuery<>(getMapperOrThrow(recordClass));
    queryDef.accept(builder);

    StartQueryExecutionResponse startResponse =
        withRetry(
            () ->
                athenaClient.startQueryExecution(
                    r -> {
                      r.queryString(builder.build())
                          .queryExecutionContext(
                              q -> q.database(builder.getMapper().getDatabaseName()));
                      if (workGroup != null) r.workGroup(workGroup);
                    }));

    GetQueryExecutionResponse finalStatus =
        waitForQueryToComplete(startResponse.queryExecutionId());

    Stream<T> stream =
        StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(
                new ResultIterator<>(startResponse.queryExecutionId(), builder),
                Spliterator.ORDERED),
            false);

    return new QueryResult<>(
        stream, startResponse.queryExecutionId(), finalStatus.queryExecution().statistics());
  }

  public <T> void createTable(Class<T> recordClass, Consumer<AthenaTable<T>> tableDef) {
    AthenaTable<T> builder = new AthenaTable<>(getMapperOrThrow(recordClass));
    tableDef.accept(builder);

    StartQueryExecutionResponse startResponse =
        withRetry(
            () ->
                athenaClient.startQueryExecution(
                    r -> {
                      r.queryString(builder.build())
                          .queryExecutionContext(
                              q -> q.database(builder.getMapper().getDatabaseName()));
                      if (workGroup != null) r.workGroup(workGroup);
                    }));

    waitForQueryToComplete(startResponse.queryExecutionId());

    if (builder.shouldRepairPartitions()) {
      String repairQuery =
          String.format(
              "MSCK REPAIR TABLE %s.%s",
              builder.getMapper().getDatabaseName(), builder.getMapper().getTableName());
      StartQueryExecutionResponse repairResponse =
          withRetry(
              () ->
                  athenaClient.startQueryExecution(
                      r -> {
                        r.queryString(repairQuery);
                        if (workGroup != null) r.workGroup(workGroup);
                      }));
      waitForQueryToComplete(repairResponse.queryExecutionId());
    }
  }

  public <T> void addPartitions(Class<T> recordClass, Consumer<AthenaPartition<T>> partitionDef) {
    AthenaPartition<T> builder = new AthenaPartition<>(getMapperOrThrow(recordClass));
    partitionDef.accept(builder);

    StartQueryExecutionResponse startResponse =
        withRetry(
            () ->
                athenaClient.startQueryExecution(
                    r -> {
                      r.queryString(builder.build());
                      if (workGroup != null) r.workGroup(workGroup);
                    }));

    waitForQueryToComplete(startResponse.queryExecutionId());
  }

  private GetQueryExecutionResponse waitForQueryToComplete(String executionId) {
    Instant deadline = Instant.now().plus(queryTimeout);
    while (true) {
      if (Instant.now().isAfter(deadline)) {
        throw new RuntimeException(
            String.format(
                "Query %s timed out after %d seconds", executionId, queryTimeout.toSeconds()));
      }

      GetQueryExecutionResponse response =
          withRetry(() -> athenaClient.getQueryExecution(r -> r.queryExecutionId(executionId)));
      QueryExecutionState state = response.queryExecution().status().state();

      if (state == QueryExecutionState.SUCCEEDED) {
        return response;
      } else if (state == QueryExecutionState.FAILED || state == QueryExecutionState.CANCELLED) {
        throw new RuntimeException(
            String.format(
                "Query %s %s: %s",
                executionId, state, response.queryExecution().status().stateChangeReason()));
      } else {
        try {
          Thread.sleep(pollingInterval.toMillis());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Query wait interrupted for " + executionId, e);
        }
      }
    }
  }

  @Override
  public void close() {
    athenaClient.close();
  }

  private class ResultIterator<T> implements Iterator<T> {
    private final String executionId;
    private final AthenaQuery<T> builder;
    private String nextToken;
    private Iterator<T> currentBatch;
    private boolean isFirstPage = true;

    ResultIterator(String executionId, AthenaQuery<T> builder) {
      this.executionId = executionId;
      this.builder = builder;
    }

    @Override
    public boolean hasNext() {
      if (currentBatch == null || !currentBatch.hasNext()) {
        if (isFirstPage || nextToken != null) {
          fetchNextBatch();
          return hasNext();
        }
        return false;
      }
      return true;
    }

    @Override
    public T next() {
      if (!hasNext()) throw new NoSuchElementException();
      return currentBatch.next();
    }

    private void fetchNextBatch() {
      GetQueryResultsResponse response =
          withRetry(
              () ->
                  athenaClient.getQueryResults(
                      r -> r.queryExecutionId(executionId).nextToken(nextToken)));
      nextToken = response.nextToken();
      currentBatch = mapResults(response, builder.getMapper(), isFirstPage).iterator();
      isFirstPage = false;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends AbstractAthenaService.Builder<Builder, AthenaService> {
    @Override
    public AthenaService build() {
      return new AthenaService(this);
    }
  }
}
