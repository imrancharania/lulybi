package com.lulybi.core.query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class LiteralFormatter {
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  public static String format(Object val) {
    if (val == null) return "NULL";
    if (val instanceof String s) {
      return "'" + s.replace("'", "''") + "'";
    }
    if (val instanceof LocalDate) return "DATE '" + val + "'";
    if (val instanceof LocalDateTime ldt) {
      return "TIMESTAMP '" + ldt.format(TIMESTAMP_FORMATTER) + "'";
    }
    if (val instanceof Instant instant) {
      return "TIMESTAMP '" + TIMESTAMP_FORMATTER.withZone(ZoneOffset.UTC).format(instant) + "'";
    }
    if (val instanceof OffsetDateTime odt) {
      return "TIMESTAMP '" + odt.format(TIMESTAMP_FORMATTER) + "'";
    }
    if (val instanceof ZonedDateTime zdt) {
      return "TIMESTAMP '" + zdt.format(TIMESTAMP_FORMATTER) + "'";
    }
    if (val instanceof Number || val instanceof Boolean) {
      return val.toString();
    }
    if (val instanceof Iterable<?> iterable) {
      return StreamSupport.stream(iterable.spliterator(), false)
          .map(LiteralFormatter::format)
          .collect(Collectors.joining(", ", "(", ")"));
    }
    if (val instanceof Object[] array) {
      return Arrays.stream(array)
          .map(LiteralFormatter::format)
          .collect(Collectors.joining(", ", "(", ")"));
    }
    return val.toString();
  }
}
