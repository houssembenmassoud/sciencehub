package io.qzz.tbsciencehubproject.pipeline.pipeline;

import java.util.Map;

class PipelineRunnerStub<T> extends PipelineRunner<T> {

  public PipelineRunnerStub(Pipeline<T> pipeline) {
    super(pipeline);
  }

  @Override
  public void handlePipelineCompleted() {
  }

  @Override
  public void handleStepSucceeded(Stage stage) {
  }

  @Override
  public void handlePipelineFailed() {
  }

  @Override
  public void handleStepStarted(Stage stage) {
  }

  public Map<PipelineStep, Stage> getStageMap() {
    return stageMap;
  }
}
