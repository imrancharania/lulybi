package com.lulybi.core.query.condition;

public class RawCondition implements Condition {
  private final String expression;

  public RawCondition(String expression) {
    this.expression = expression;
  }

  @Override
  public String render() {
    return expression;
  }
}
