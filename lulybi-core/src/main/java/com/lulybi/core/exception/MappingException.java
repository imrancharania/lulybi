package com.lulybi.core.exception;

public class MappingException extends RuntimeException {

  private final String columnName;
  private final String expectedType;
  private final Object actualValue;

  public MappingException(
      String message, String columnName, String expectedType, Object actualValue) {
    super(message);
    this.columnName = columnName;
    this.expectedType = expectedType;
    this.actualValue = actualValue;
  }

  public String getColumnName() {
    return columnName;
  }

  public String getExpectedType() {
    return expectedType;
  }

  public Object getActualValue() {
    return actualValue;
  }
}
