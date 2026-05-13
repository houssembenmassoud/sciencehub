package io.qzz.tbsciencehubproject.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import lombok.Getter;

@Getter
@SuppressWarnings("unused")
public abstract class TypeToken<T> {

  private final Type type;
  private final Class<?> rawType;

  public TypeToken() {
    Type superClass = getClass().getGenericSuperclass();
    type = ((ParameterizedType) superClass).getActualTypeArguments()[0];

    switch (type) {
      case Class<?> clazz -> this.rawType = clazz;
      case ParameterizedType parameterizedType ->
          this.rawType = (Class<?>) parameterizedType.getRawType();
      default -> throw new IllegalArgumentException("Illegal type: " + type);
    }
  }

  private TypeToken(Class<T> rawType) {
    this.rawType = rawType;
    this.type = rawType;
  }

  public static <T> TypeToken<T> of(Class<T> type) {
    return new TypeToken<>(type) {};
  }
}
