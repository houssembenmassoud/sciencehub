package io.qzz.tbsciencehubproject.resource;

import io.qzz.tbsciencehubproject.utils.TypeToken;

public final class ResourceKeyImpl<T> implements ResourceKey<T> {

  private final TypeToken<T> type;

  public ResourceKeyImpl(TypeToken<T> type) {
    this.type = type;
  }

  @Override
  public TypeToken<T> getType() {
    return type;
  }
}
