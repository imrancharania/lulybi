package com.lulybi.athena;

import software.amazon.awssdk.services.athena.model.QueryExecutionStatistics;

/**
 * Encapsulates the results of a query along with Athena execution metadata.
 *
 * @param <R> The type of the results (e.g., List&lt;T&gt; or Stream&lt;T&gt;)
 */
public record QueryResult<R>(
    R results, String queryExecutionId, QueryExecutionStatistics statistics) {
  public long getDataScannedInBytes() {
    return statistics != null ? statistics.dataScannedInBytes() : 0L;
  }
}
