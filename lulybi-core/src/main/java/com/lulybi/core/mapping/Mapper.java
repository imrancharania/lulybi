package com.lulybi.core.mapping;

import java.util.Map;

public interface Mapper<T> {
  T map(Map<String, String> row);

  /** Returns the physical table name defined in the annotation. */
  String getTableName();

  /** Returns the database/schema name. */
  String getDatabaseName();

  /** Returns a map of column names to their SQL types. */
  Map<String, String> getColumnDefinitions();

  /** Returns a map of partition column names to their SQL types. */
  default Map<String, String> getPartitionDefinitions() {
    return java.util.Collections.emptyMap();
  }
}
