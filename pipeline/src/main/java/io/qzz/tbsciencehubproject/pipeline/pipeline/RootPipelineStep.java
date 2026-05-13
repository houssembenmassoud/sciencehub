package io.qzz.tbsciencehubproject.pipeline.pipeline;

import io.qzz.tbsciencehubproject.resource.ResourceKey;
import io.qzz.tbsciencehubproject.resource.ResourceKeyImpl;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RootPipelineStep<I> implements PipelineStep {

  private final Pipeline<I> pipeline;
  private final ResourceKey<I> rootArtifactKey;
  private final Artifact<I> rootArtifact;

  public RootPipelineStep(Pipeline<I> pipeline) {
    this.pipeline = pipeline;
    this.rootArtifactKey = new ResourceKeyImpl<>(pipeline.inputType());
    this.rootArtifact = new Artifact<>() {
      @Override
      public ResourceKey<I> key() {
        return rootArtifactKey;
      }

      @Override
      public PipelineStep step() {
        return RootPipelineStep.this;
      }
    };
  }

  @Override
  public String name() {
    return pipeline.name();
  }

  @Override
  public void dependOn(Artifact<?> step) {
    throw new UnsupportedOperationException("Root step cannot depend on anything");
  }

  @Override
  public Collection<PipelineStep> dependencySteps() {
    return Collections.emptyList();
  }

  @Override
  public Collection<Artifact<?>> dependencies() {
    return Collections.emptyList();
  }

  @Override
  public Collection<Artifact<?>> provides() {
    return List.of(rootArtifact);
  }

  @Override
  public <T> Optional<Artifact<?>> provided(ResourceKey<T> key) {
    if (key == rootArtifactKey) {
      return Optional.of(rootArtifact);
    }
    return Optional.empty();
  }

  public Artifact<I> rootArtifact() {
    return rootArtifact;
  }
}
