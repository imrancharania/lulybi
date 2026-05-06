package com.lulybi.core.query.operator;

public enum Operator {
  EQUALS("="),
  NOT_EQUALS("<>"),
  GREATER_THAN(">"),
  GREATER_THAN_OR_EQUAL(">="),
  LESS_THAN("<"),
  LESS_THAN_OR_EQUAL("<="),
  LIKE("LIKE"),
  IS_NULL("IS NULL"),
  IS_NOT_NULL("IS NOT NULL");

  private final String symbol;

  Operator(String symbol) {
    this.symbol = symbol;
  }

  public String getSymbol() {
    return symbol;
  }
}
