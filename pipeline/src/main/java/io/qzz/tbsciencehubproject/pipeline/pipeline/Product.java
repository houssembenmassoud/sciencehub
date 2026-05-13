package io.qzz.tbsciencehubproject.pipeline.pipeline;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Product.Result.Failure;
import io.qzz.tbsciencehubproject.pipeline.pipeline.Product.Result.Success;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public interface Product<T> {

  Artifact<T> artifact();

  Optional<Result<T>> result();

  boolean complete(T result);

  boolean fail(Throwable cause);

  boolean completed();

  boolean failed();

  boolean succeeded();


  sealed interface Result<T> permits Failure, Success {

    T get() throws ExecutionException;

    default Failure<T> asFailure() {
      return switch (this) {
        case Success<T> ignore -> throw new ClassCastException("Result is not failed to be converted to failed!");
        case Failure<T> failure -> failure;
      };
    }

    default Success<T> asSuccess() {
      return switch (this) {
        case Success<T> success -> success;
        case Failure<T> ignore -> throw new ClassCastException("Result is not succeeded to be converted to success!");
      };
    }

    record Success<T>(T result) implements Result<T> {

      @Override
      public T get() {
        return result;
      }

    }

    record Failure<T>(Throwable error) implements Result<T> {

      @Override
      public T get() throws ExecutionException {
        throw new ExecutionException(error);
      }

    }
  }
}
