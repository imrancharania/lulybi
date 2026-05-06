package com.lulybi.athena;

import com.lulybi.core.mapping.Mapper;
import com.lulybi.core.query.LiteralFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AthenaPartition<T> {
  private final Mapper<T> mapper;
  private final List<Entry> entries = new ArrayList<>();

  AthenaPartition(Mapper<T> mapper) {
    this.mapper = mapper;
  }

  public AthenaPartition<T> add(Map<String, Object> spec) {
    return add(spec, null);
  }

  public AthenaPartition<T> add(Map<String, Object> spec, String location) {
    entries.add(new Entry(spec, location));
    return this;
  }

  Mapper<T> getMapper() {
    return mapper;
  }

  public String build() {
    if (entries.isEmpty()) {
      throw new IllegalStateException("At least one partition must be specified");
    }

    StringBuilder sb = new StringBuilder("ALTER TABLE ");
    sb.append(mapper.getDatabaseName()).append(".").append(mapper.getTableName());
    sb.append(" ADD IF NOT EXISTS");

    for (Entry entry : entries) {
      String specStr =
          entry.spec.entrySet().stream()
              .map(e -> e.getKey() + "=" + LiteralFormatter.format(e.getValue()))
              .collect(Collectors.joining(", "));
      sb.append("\n  PARTITION (").append(specStr).append(")");
      if (entry.location != null && !entry.location.isBlank()) {
        sb.append(" LOCATION '").append(entry.location).append("'");
      }
    }

    return sb.toString();
  }

  private record Entry(Map<String, Object> spec, String location) {}
}
