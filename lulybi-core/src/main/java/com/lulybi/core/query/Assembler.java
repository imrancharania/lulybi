package com.lulybi.core.query;

import com.lulybi.core.query.condition.Condition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A reusable base for assembling SQL queries. Spokes can extend this to handle dialect-specific
 * syntax (e.g., LIMIT vs TOP).
 */
public class Assembler {
  protected final String tableName;
  protected final List<String> selectColumns;
  protected final List<Condition> conditions;
  protected final List<String> groupByColumns;
  protected final String orderByColumn;
  protected final SortOrder sortOrder;
  protected final Integer limit;

  private Assembler(QueryBuilder builder) {
    this.tableName = builder.tableName;
    this.selectColumns = builder.selectColumns;
    this.conditions = builder.conditions;
    this.groupByColumns = builder.groupByColumns;
    this.orderByColumn = builder.orderByColumn;
    this.sortOrder = builder.sortOrder;
    this.limit = builder.limit;
  }

  /** Minimal constructor for internal use. */
  private Assembler(String tableName) {
    this.tableName = tableName;
    this.selectColumns = null;
    this.conditions = null;
    this.groupByColumns = null;
    this.orderByColumn = null;
    this.sortOrder = null;
    this.limit = null;
  }

  public static QueryBuilder queryBuilder(String tableName) {
    return new QueryBuilder(tableName);
  }

  public static TableBuilder tableBuilder(String tableName) {
    return new TableBuilder(tableName);
  }

  protected String assembleQuery() {
    StringBuilder sb = new StringBuilder("SELECT ");
    appendColumns(sb);
    sb.append(" FROM ").append(tableName);
    appendWhere(sb);
    appendGroupBy(sb);
    appendOrderBy(sb);
    appendLimit(sb);
    return sb.toString();
  }

  protected String assembleTable(
      String databaseName,
      Map<String, String> columns,
      Map<String, String> partitionColumns,
      String suffix) {
    StringBuilder sb = new StringBuilder("CREATE EXTERNAL TABLE IF NOT EXISTS ");
    if (databaseName != null && !databaseName.isBlank()) {
      sb.append(databaseName).append(".");
    }
    sb.append(tableName).append(" (\n");

    if (columns != null) {
      String columnBody =
          columns.entrySet().stream()
              .map(e -> "  " + e.getKey() + " " + e.getValue())
              .collect(Collectors.joining(",\n"));
      sb.append(columnBody);
    }

    sb.append("\n)");

    if (partitionColumns != null && !partitionColumns.isEmpty()) {
      sb.append("\nPARTITIONED BY (\n");
      String partitionBody =
          partitionColumns.entrySet().stream()
              .map(e -> "  " + e.getKey() + " " + e.getValue())
              .collect(Collectors.joining(",\n"));
      sb.append(partitionBody);
      sb.append("\n)");
    }

    if (suffix != null && !suffix.isBlank()) {
      sb.append("\n").append(suffix);
    }

    return sb.append(";").toString();
  }

  protected void appendColumns(StringBuilder sb) {
    if (selectColumns == null || selectColumns.isEmpty()) {
      sb.append("*");
    } else {
      sb.append(String.join(", ", selectColumns));
    }
  }

  protected void appendWhere(StringBuilder sb) {
    if (conditions != null && !conditions.isEmpty()) {
      String whereClause =
          conditions.stream()
              .map(Condition::render)
              .filter(Objects::nonNull)
              .filter(s -> !s.isBlank())
              .collect(Collectors.joining(" AND "));

      if (!whereClause.isEmpty()) {
        sb.append(" WHERE ").append(whereClause);
      }
    }
  }

  protected void appendGroupBy(StringBuilder sb) {
    if (groupByColumns != null && !groupByColumns.isEmpty()) {
      sb.append(" GROUP BY ").append(String.join(", ", groupByColumns));
    }
  }

  protected void appendOrderBy(StringBuilder sb) {
    if (orderByColumn != null) {
      sb.append(" ORDER BY ").append(orderByColumn);
      Optional.ofNullable(sortOrder).ifPresent(so -> sb.append(" ").append(so.name()));
    }
  }

  protected void appendLimit(StringBuilder sb) {
    if (limit != null) {
      // Standard ANSI/Presto/Postgres limit
      sb.append(" LIMIT ").append(limit);
    }
  }

  public static class QueryBuilder {
    private final String tableName;
    private List<String> selectColumns = new ArrayList<>();
    private List<Condition> conditions = new ArrayList<>();
    private List<String> groupByColumns = new ArrayList<>();
    private String orderByColumn;
    private SortOrder sortOrder;
    private Integer limit;

    private QueryBuilder(String tableName) {
      this.tableName = tableName;
    }

    public QueryBuilder select(List<String> columns) {
      this.selectColumns = columns;
      return this;
    }

    public QueryBuilder where(List<Condition> conditions) {
      this.conditions = conditions;
      return this;
    }

    public QueryBuilder groupBy(List<String> columns) {
      this.groupByColumns = columns;
      return this;
    }

    public QueryBuilder orderBy(String column, SortOrder order) {
      this.orderByColumn = column;
      this.sortOrder = order;
      return this;
    }

    public QueryBuilder limit(Integer limit) {
      this.limit = limit;
      return this;
    }

    public String build() {
      return new Assembler(this).assembleQuery();
    }
  }

  public static class TableBuilder {
    private final String tableName;
    private String databaseName;
    private Map<String, String> columns;
    private Map<String, String> partitionColumns;
    private String suffix;

    private TableBuilder(String tableName) {
      this.tableName = tableName;
    }

    public TableBuilder database(String databaseName) {
      this.databaseName = databaseName;
      return this;
    }

    public TableBuilder columns(Map<String, String> columns) {
      this.columns = columns;
      return this;
    }

    public TableBuilder partitionColumns(Map<String, String> partitionColumns) {
      this.partitionColumns = partitionColumns;
      return this;
    }

    public TableBuilder suffix(String suffix) {
      this.suffix = suffix;
      return this;
    }

    public String build() {
      return new Assembler(tableName)
          .assembleTable(databaseName, columns, partitionColumns, suffix);
    }
  }
}
