package com.lulybi.core.query.condition;

public class RangeCondition implements Condition {
  private final String column;
  private final Object start;
  private final Object end;

  public RangeCondition(String column, Object start, Object end) {
    this.column = column;
    this.start = start;
    this.end = end;
  }

  @Override
  public String render() {
    return String.format(
        "%s BETWEEN %s AND %s", column, Condition.formatValue(start), Condition.formatValue(end));
  }
}
