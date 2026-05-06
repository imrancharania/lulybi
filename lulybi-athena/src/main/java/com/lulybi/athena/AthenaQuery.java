package com.lulybi.athena;

import com.lulybi.core.mapping.Mapper;
import com.lulybi.core.query.Assembler;
import com.lulybi.core.query.SortOrder;
import com.lulybi.core.query.condition.BinaryCondition;
import com.lulybi.core.query.condition.Condition;
import com.lulybi.core.query.condition.ExistsCondition;
import com.lulybi.core.query.condition.GroupCondition;
import com.lulybi.core.query.condition.InCondition;
import com.lulybi.core.query.condition.MatchCondition;
import com.lulybi.core.query.condition.RangeCondition;
import com.lulybi.core.query.condition.RawCondition;
import com.lulybi.core.query.operator.LogicalOperator;
import com.lulybi.core.query.operator.Operator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AthenaQuery<T> {

  private static final String COUNT_STAR = "COUNT(*)";
  private static final String SUM_FORMAT = "SUM(%s)";

  private final Mapper<T> mapper;
  private final List<String> selectColumns = new ArrayList<>();
  private final List<Condition> conditions = new ArrayList<>();
  private final List<String> groupByColumns = new ArrayList<>();
  private String orderByColumn;
  private SortOrder sortOrder;
  private Integer limit;

  AthenaQuery(Mapper<T> mapper) {
    this.mapper = mapper;
  }

  public AthenaQuery<T> select(String... columns) {
    this.selectColumns.addAll(List.of(columns));
    return this;
  }

  public AthenaQuery<T> count() {
    this.selectColumns.add(COUNT_STAR);
    return this;
  }

  public AthenaQuery<T> sum(String column) {
    this.selectColumns.add(String.format(SUM_FORMAT, column));
    return this;
  }

  public AthenaQuery<T> groupBy(String... columns) {
    this.groupByColumns.addAll(List.of(columns));
    return this;
  }

  public AthenaQuery<T> where(String column, Operator operator, Object value) {
    this.conditions.add(new BinaryCondition(column, operator, value));
    return this;
  }

  public AthenaQuery<T> where(Condition condition) {
    this.conditions.add(condition);
    return this;
  }

  public AthenaQuery<T> whereRaw(String expression) {
    this.conditions.add(new RawCondition(expression));
    return this;
  }

  public AthenaQuery<T> exists(String subquery) {
    this.conditions.add(new ExistsCondition(subquery, false));
    return this;
  }

  public AthenaQuery<T> notExists(String subquery) {
    this.conditions.add(new ExistsCondition(subquery, true));
    return this;
  }

  public AthenaQuery<T> match(String column, String pattern) {
    this.conditions.add(new MatchCondition(column, pattern));
    return this;
  }

  public AthenaQuery<T> in(String column, Collection<?> values) {
    List<?> valuesCopy = values == null ? List.of() : new ArrayList<>(values);
    this.conditions.add(new InCondition(column, valuesCopy));
    return this;
  }

  public AthenaQuery<T> between(String column, Object start, Object end) {
    this.conditions.add(new RangeCondition(column, start, end));
    return this;
  }

  public AthenaQuery<T> and(Condition... nested) {
    GroupCondition group = new GroupCondition(LogicalOperator.AND);
    for (Condition c : nested) group.add(c);
    this.conditions.add(group);
    return this;
  }

  public AthenaQuery<T> or(Condition... nested) {
    GroupCondition group = new GroupCondition(LogicalOperator.OR);
    for (Condition c : nested) group.add(c);
    this.conditions.add(group);
    return this;
  }

  public AthenaQuery<T> orderBy(String column) {
    return orderBy(column, SortOrder.ASC);
  }

  public AthenaQuery<T> orderBy(String column, SortOrder order) {
    this.orderByColumn = column;
    this.sortOrder = order;
    return this;
  }

  public AthenaQuery<T> limit(int limit) {
    this.limit = limit;
    return this;
  }

  Mapper<T> getMapper() {
    return mapper;
  }

  public String build() {
    var queryBuilder =
        Assembler.queryBuilder(mapper.getTableName())
            .select(selectColumns)
            .where(conditions)
            .groupBy(groupByColumns);

    if (orderByColumn != null) {
      queryBuilder.orderBy(orderByColumn, sortOrder);
    }

    if (limit != null) {
      queryBuilder.limit(limit);
    }

    return queryBuilder.build();
  }
}
