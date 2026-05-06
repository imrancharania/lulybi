package com.lulybi.core.registry;

import com.lulybi.core.mapping.Mapper;
import java.util.concurrent.ConcurrentHashMap;

public final class MapperRegistry {

  private static final ConcurrentHashMap<Class<?>, Mapper<?>> MAPPERS = new ConcurrentHashMap<>();

  public static <T> void register(Class<T> clazz, Mapper<T> mapper) {
    MAPPERS.put(clazz, mapper);
  }

  @SuppressWarnings("unchecked")
  public static <T> Mapper<T> getMapper(Class<T> clazz) {
    return (Mapper<T>) MAPPERS.get(clazz);
  }
}
