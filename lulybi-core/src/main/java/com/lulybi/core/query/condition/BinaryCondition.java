package com.lulybi.core.query.condition;

import com.lulybi.core.query.operator.Operator;

public class BinaryCondition implements Condition {
  private final String column;
  private final Operator operator;
  private final Object value;

  public BinaryCondition(String column, Operator operator, Object value) {
    this.column = column;
    this.operator = operator;
    this.value = value;
  }

  @Override
  public String render() {
    return switch (operator) {
      case IS_NULL, IS_NOT_NULL -> String.format("%s %s", column, operator.getSymbol());
      default ->
          String.format("%s %s %s", column, operator.getSymbol(), Condition.formatValue(value));
    };
  }
}
