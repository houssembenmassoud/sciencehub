package io.qzz.tbsciencehubproject.resource;

import io.qzz.tbsciencehubproject.utils.TypeToken;

public interface ResourceKey<T> {

  TypeToken<T> getType();
 static <T> ResourceKey<T> of(String name, Class<T> type) {
    return new ResourceKeyImpl<>(TypeToken.of(type));
  }
}
