package com.lulybi.core.query.condition;

import com.lulybi.core.query.operator.LogicalOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GroupCondition implements Condition {
  private final List<Condition> conditions = new ArrayList<>();
  private final LogicalOperator operator;

  public GroupCondition(LogicalOperator operator) {
    this.operator = operator;
  }

  public GroupCondition add(Condition condition) {
    this.conditions.add(condition);
    return this;
  }

  @Override
  public String render() {
    if (conditions.isEmpty()) {
      return "";
    }
    if (conditions.size() == 1) {
      return conditions.get(0).render();
    }

    String joined =
        conditions.stream()
            .map(Condition::render)
            .collect(Collectors.joining(" " + operator.name() + " "));

    return "(" + joined + ")";
  }
}
