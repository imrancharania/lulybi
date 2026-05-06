package com.lulybi.core.query.condition;

public class ExistsCondition implements Condition {
  private final String subquery;
  private final boolean negated;

  public ExistsCondition(String subquery, boolean negated) {
    this.subquery = subquery;
    this.negated = negated;
  }

  @Override
  public String render() {
    return (negated ? "NOT EXISTS (" : "EXISTS (") + subquery + ")";
  }
}
