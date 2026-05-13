package io.qzz.tbsciencehubproject.pipeline.pipeline;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Product.Result.Failure;
import io.qzz.tbsciencehubproject.pipeline.pipeline.Product.Result.Success;
import java.util.Optional;

final class ProductImpl<T> implements Product<T> {

  private final Artifact<T> artifact;
  private Result<T> result = null;

  ProductImpl(Artifact<T> artifact) {
    this.artifact = artifact;
  }

  @Override
  public Artifact<T> artifact() {
    return artifact;
  }

  @Override
  public Optional<Result<T>> result() {
    return Optional.ofNullable(result);
  }

  @Override
  public boolean complete(T result) {
    if (completed()) {
      return false;
    }

    this.result = new Success<>(result);

    return true;
  }

  @Override
  public boolean fail(Throwable cause) {
    if (completed()) {
      return false;
    }

    this.result = new Failure<>(cause);

    return true;
  }

  @Override
  public boolean completed() {
    return result != null;
  }

  @Override
  public boolean failed() {
    return result().map(r -> r instanceof Result.Failure<T>).orElse(false);
  }

  @Override
  public boolean succeeded() {
    return result().map(r -> r instanceof Result.Success<T>).orElse(false);
  }
}
