package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineStep;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractPipelineStep implements PipelineStep {

  private final String stepName;
  private final CopyOnWriteArraySet<Artifact<?>> dependencies = new CopyOnWriteArraySet<>();

  protected AbstractPipelineStep(String stepName) {
    this.stepName = stepName;
  }

  @Override
  public String name() {
    return stepName;
  }

  @Override
  public void dependOn(Artifact<?> artifact) {
    dependencies.add(artifact);
  }

  @Override
  public Collection<Artifact<?>> dependencies() {
    return List.copyOf(dependencies);
  }
}
