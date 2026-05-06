package com.lulybi.athena;

public enum SerDe {
  JSON("org.openx.data.jsonserde.JsonSerDe"),
  PARQUET("org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe"),
  ORC("org.apache.hadoop.hive.ql.io.orc.OrcSerde"),
  CSV("org.apache.hadoop.hive.serde2.OpenCSVSerde"),
  TEXT("org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe");

  private final String className;

  SerDe(String className) {
    this.className = className;
  }

  public String getClassName() {
    return className;
  }
}
