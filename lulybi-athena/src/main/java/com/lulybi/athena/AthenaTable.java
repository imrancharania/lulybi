package com.lulybi.athena;

import com.lulybi.core.mapping.Mapper;
import com.lulybi.core.query.Assembler;
import java.util.LinkedHashMap;
import java.util.Map;

public class AthenaTable<T> {
  private final Mapper<T> mapper;
  private String s3Location;
  private SerDe serde = SerDe.JSON;
  private final Map<String, String> partitionColumns = new LinkedHashMap<>();
  private boolean repairPartitions = false;

  AthenaTable(Mapper<T> mapper) {
    this.mapper = mapper;
    // Automatically pull partition definitions from the mapper if available
    this.partitionColumns.putAll(mapper.getPartitionDefinitions());
  }

  public AthenaTable<T> location(String s3Location) {
    this.s3Location = s3Location;
    return this;
  }

  public AthenaTable<T> serde(SerDe serde) {
    this.serde = serde;
    return this;
  }

  public AthenaTable<T> partitionBy(String columnName, String type) {
    this.partitionColumns.put(columnName, type);
    return this;
  }

  /**
   * Automatically run MSCK REPAIR TABLE after table creation. Only applicable if partition columns
   * are defined.
   */
  public AthenaTable<T> repairPartitions() {
    this.repairPartitions = true;
    return this;
  }

  public boolean shouldRepairPartitions() {
    return repairPartitions && !partitionColumns.isEmpty();
  }

  Mapper<T> getMapper() {
    return mapper;
  }

  public String build() {
    if (s3Location == null || s3Location.isBlank()) {
      throw new IllegalStateException("S3 location must be specified for Athena table creation");
    }

    String suffix =
        String.format("ROW FORMAT SERDE '%s'\nLOCATION '%s'", serde.getClassName(), s3Location);

    Map<String, String> columns = new LinkedHashMap<>(mapper.getColumnDefinitions());
    // In many cases, columns will already have partitions removed by the processor,
    // but we ensure it here for safety and manual overrides.
    partitionColumns.keySet().forEach(columns::remove);

    return Assembler.tableBuilder(mapper.getTableName())
        .database(mapper.getDatabaseName())
        .columns(columns)
        .partitionColumns(partitionColumns)
        .suffix(suffix)
        .build();
  }
}
