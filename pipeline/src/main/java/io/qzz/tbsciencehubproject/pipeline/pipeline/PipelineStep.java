package io.qzz.tbsciencehubproject.pipeline.pipeline;

import io.qzz.tbsciencehubproject.resource.ResourceKey;
import java.util.Collection;
import java.util.Optional;

public interface PipelineStep {

  String name();

  void dependOn(Artifact<?> step);

  Collection<PipelineStep> dependencySteps();

  Collection<Artifact<?>> dependencies();

  Collection<Artifact<?>> provides();

  <T> Optional<Artifact<?>> provided(ResourceKey<T> key);

}
