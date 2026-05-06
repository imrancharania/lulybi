package com.lulybi.athena;

import com.lulybi.core.mapping.Mapper;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import software.amazon.awssdk.services.athena.AthenaAsyncClient;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;

public class AthenaQueryResultSpliterator<T> implements Spliterator<T> {

  private final AthenaAsyncClient athenaAsyncClient;
  private final String queryExecutionId;
  private final Mapper<T> rowMapper;
  private final ScheduledExecutorService scheduler;

  private String nextToken = null;
  private List<T> currentPage = null;
  private int currentIndex = 0;
  private boolean isFirstPage = true;

  public AthenaQueryResultSpliterator(
      AthenaAsyncClient athenaAsyncClient,
      String queryExecutionId,
      Mapper<T> rowMapper,
      ScheduledExecutorService scheduler) {
    this.athenaAsyncClient = Objects.requireNonNull(athenaAsyncClient);
    this.queryExecutionId = Objects.requireNonNull(queryExecutionId);
    this.rowMapper = Objects.requireNonNull(rowMapper);
    this.scheduler = Objects.requireNonNull(scheduler);
  }

  @Override
  public boolean tryAdvance(Consumer<? super T> action) {
    Objects.requireNonNull(action);

    if (currentPage == null || currentIndex >= currentPage.size()) {
      if (nextToken == null && !isFirstPage) {
        return false; // No more pages
      }
      if (!fetchNextPage()) {
        return false; // Failed to fetch or no more data
      }
    }

    if (currentIndex < currentPage.size()) {
      action.accept(currentPage.get(currentIndex));
      currentIndex++;
      return true;
    }
    return false;
  }

  private boolean fetchNextPage() {
    try {
      CompletableFuture<GetQueryResultsResponse> future =
          AsyncAthenaService.withRetryAsync(
              () ->
                  athenaAsyncClient.getQueryResults(
                      r -> r.queryExecutionId(queryExecutionId).nextToken(nextToken)),
              scheduler);

      GetQueryResultsResponse response = future.join(); // Block until results are available
      currentPage = AsyncAthenaService.mapResults(response, rowMapper, isFirstPage);
      nextToken = response.nextToken();
      currentIndex = 0;
      isFirstPage = false;
      return !currentPage.isEmpty() || nextToken != null;
    } catch (CompletionException e) {
      // Unwrap the exception and rethrow or handle appropriately
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw e;
    }
  }

  @Override
  public Spliterator<T> trySplit() {
    return null; // Not splittable for parallel processing
  }

  @Override
  public long estimateSize() {
    return Long.MAX_VALUE; // Unknown size
  }

  @Override
  public int characteristics() {
    return ORDERED | IMMUTABLE | NONNULL;
  }
}
