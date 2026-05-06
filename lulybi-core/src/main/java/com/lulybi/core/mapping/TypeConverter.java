package com.lulybi.core.mapping;

import com.lulybi.core.exception.MappingException;
import java.time.Instant;

public final class TypeConverter {

  public static int asInt(String value, String column) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new MappingException("Cannot parse int", column, "int", value);
    }
  }

  public static long asLong(String value, String column) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new MappingException("Cannot parse long", column, "long", value);
    }
  }

  public static Instant asInstant(String value, String column) {
    try {
      return Instant.parse(value);
    } catch (Exception e) {
      throw new MappingException("Cannot parse ISO-8601 timestamp", column, "Instant", value);
    }
  }

  public static boolean asBoolean(String value, String column) {
    return Boolean.parseBoolean(value);
  }
}
