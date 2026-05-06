package com.lulybi.core.query.condition;

import java.util.Collection;
import java.util.stream.Collectors;

public class InCondition implements Condition {
  private final String column;
  private final Collection<?> values;

  public InCondition(String column, Collection<?> values) {
    this.column = column;
    this.values = values;
  }

  @Override
  public String render() {
    String formattedValues =
        values.stream().map(Condition::formatValue).collect(Collectors.joining(", ", "(", ")"));
    return String.format("%s IN %s", column, formattedValues);
  }
}
