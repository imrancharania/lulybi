package com.lulybi.core.query.condition;

public class MatchCondition implements Condition {
  private final String column;
  private final String pattern;

  public MatchCondition(String column, String pattern) {
    this.column = column;
    this.pattern = pattern;
  }

  @Override
  public String render() {
    return String.format("regexp_like(%s, %s)", column, Condition.formatValue(pattern));
  }
}
