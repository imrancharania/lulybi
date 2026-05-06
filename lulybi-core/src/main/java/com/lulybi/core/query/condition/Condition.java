package com.lulybi.core.query.condition;

import com.lulybi.core.query.LiteralFormatter;

public interface Condition {
  String render();

  static String formatValue(Object val) {
    return LiteralFormatter.format(val);
  }
}
