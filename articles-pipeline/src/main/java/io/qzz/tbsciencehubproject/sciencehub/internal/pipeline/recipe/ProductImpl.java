package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact;
import io.qzz.tbsciencehubproject.pipeline.pipeline.Product;
import io.qzz.tbsciencehubproject.pipeline.pipeline.Product.Result.Failure;
import io.qzz.tbsciencehubproject.pipeline.pipeline.Product.Result.Success;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ProductImpl<T> implements Product<T> {

  private final AtomicReference<Result<T>> product = new AtomicReference<>();

  private final Artifact<T> artifact;

  public ProductImpl(Artifact<T> artifact) {
    this.artifact = artifact;
  }

  @Override
  public Artifact<T> artifact() {
    return artifact;
  }

  @Override
  public Optional<Result<T>> result() {
    if (!completed()) return Optional.empty();

    return Optional.of(product.get());
  }

  @Override
  public boolean complete(T result) {
    return product.compareAndSet(null, new Success<>(result));
  }

  @Override
  public boolean fail(Throwable cause) {
    return product.compareAndSet(null, new Failure<>(cause));
  }

  @Override
  public boolean completed() {
    return product.get() != null;
  }

  @Override
  public boolean failed() {
    return switch (product.get()) {
      case Failure<T> ignore -> true;
      case Success<T> ignore -> false;
    };
  }

  @Override
  public boolean succeeded() {
    return switch (product.get()) {
      case Failure<T> ignore -> false;
      case Success<T> ignore -> true;
    };
  }
}
