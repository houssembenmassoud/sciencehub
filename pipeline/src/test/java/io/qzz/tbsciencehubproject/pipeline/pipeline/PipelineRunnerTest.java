package io.qzz.tbsciencehubproject.pipeline.pipeline;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineRunner.PipelineState;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineRunner.Stage;
import io.qzz.tbsciencehubproject.resource.ResourceKey;
import io.qzz.tbsciencehubproject.resource.ResourceKeyImpl;
import io.qzz.tbsciencehubproject.utils.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PipelineRunnerTest {

  @Test
  void test_runner_reject_null_pipeline() {
    assertThrows(NullPointerException.class, () -> new PipelineRunnerStub<>(null));
  }

  @Test
  void test_runner_reject_null_start_value() {
    Pipeline<?> pipeline = mock(Pipeline.class);

    assertThrows(NullPointerException.class, () -> new PipelineRunnerStub<>(pipeline));
  }

  @Test
  void test_runner_reject_both_null() {
    assertThrows(NullPointerException.class, () -> new PipelineRunnerStub<>(null));
  }

  @Test
  void test_runner_reject_empty_pipeline() {
    Pipeline<Object> pipeline = mock(Pipeline.class);

    assertThrows(NullPointerException.class, () -> new PipelineRunnerStub<>(pipeline));
  }

  @Test
  void test_runner_reject_invalid_degenerate_pipeline() {
    Pipeline<Object> pipeline = mock(Pipeline.class);
    when(pipeline.inputType()).thenReturn(TypeToken.of(Object.class));
    var rootStep = new RootPipelineStep<>(pipeline);
    when(pipeline.rootStep()).thenReturn(rootStep);
    when(pipeline.steps()).thenReturn(Collections.emptyList());

    assertThrows(IllegalArgumentException.class, () -> new PipelineRunnerStub<>(pipeline));
  }

  @Test
  void test_runner_degenerate_pipeline_completes_immediately() {
    var pipeline = mockPipeline("pipeline");

    var runnerSpy = Mockito.spy(new PipelineRunnerStub<>(pipeline));
    ArgumentCaptor<Stage> stageCaptor = ArgumentCaptor.forClass(Stage.class);

    runnerSpy.start(new Object());

    assertThat(runnerSpy.state()).isEqualTo(PipelineState.COMPLETED);
    verify(runnerSpy, times(1)).handlePipelineCompleted();
    verify(runnerSpy, never()).handlePipelineFailed();
    verify(runnerSpy, times(1)).handleStepStarted(any());
    verify(runnerSpy, times(1)).handleStepSucceeded(stageCaptor.capture());
    assertThat(stageCaptor.getValue()).extracting(Stage::step).isEqualTo(pipeline.rootStep());
  }

  @Test
  void test_runner_child_steps_start_when_parent_step_completes() {
    var pipeline = mockPipeline("pipeline");

    var step1 = mockStep("step1", pipeline.rootStep(), pipeline);
    var step2 = mockStep("step2", pipeline.rootStep(), pipeline);

    var runnerSpy = Mockito.spy(new PipelineRunnerStub<>(pipeline));
    ArgumentCaptor<Stage> stageCaptor = ArgumentCaptor.forClass(Stage.class);

    runnerSpy.start(new Object());

    verify(runnerSpy, times(3)).handleStepStarted(stageCaptor.capture());
    var captured = stageCaptor.getAllValues().stream().map(Stage::step).toList();
    assertThat(step1).isIn(captured);
    assertThat(step2).isIn(captured);
  }


  @Test
  void test_runner_idle_to_running_transition() {
    var pipeline = mockPipeline("pipeline");
    mockStep("step1", pipeline.rootStep(), pipeline);

    var runnerSpy = Mockito.spy(new PipelineRunnerStub<>(pipeline));

    assertThat(runnerSpy.state()).isEqualTo(PipelineState.IDLE);
    runnerSpy.start(new Object());
    assertThat(runnerSpy.state()).isEqualTo(PipelineState.RUNNING);
  }

  @Test
  void test_runner_running_to_running_transition() {
    var pipeline = mockPipeline("pipeline");
    var step1 = mockStep("step1", pipeline.rootStep(), pipeline);
    mockStep("step2", step1, pipeline);

    var runner = new PipelineRunnerStub<>(pipeline);
    var runnerSpy = Mockito.spy(runner);

    runnerSpy.start(new Object());

    @SuppressWarnings("unchecked")
    Artifact<Object> artifact = (Artifact<Object>) step1.provides().stream().findAny()
        .orElseThrow();

    assertThat(runnerSpy.completeArtifact(artifact, new Object())).isTrue();

    verify(runnerSpy, times(3)).handleStepStarted(any());
    verify(runnerSpy, times(2)).handleStepSucceeded(any());
    assertThat(runnerSpy.state()).isEqualTo(PipelineState.RUNNING);
  }

  @Test
  void test_runner_running_to_failing_transition() {
    var pipeline = mockPipeline("pipeline");
    var step1 = mockStep("step1", pipeline.rootStep(), pipeline);
    mockStep("step2", step1, pipeline);

    var runnerSpy = Mockito.spy(new PipelineRunnerStub<>(pipeline));

    runnerSpy.start(new Object());

    @SuppressWarnings("unchecked")
    Artifact<Object> artifact = (Artifact<Object>) step1.provides().stream().findAny()
        .orElseThrow();

    assertThat(runnerSpy.failArtifact(artifact, new RuntimeException())).isTrue();
    assertThat(runnerSpy.state()).isEqualTo(PipelineState.FAILED);
    verify(runnerSpy, times(1)).handlePipelineFailed();
  }

  @Test
  void test_runner_running_to_complete_transition() {
    var pipeline = mockPipeline("pipeline");
    var step1 = mockStep("step1", pipeline.rootStep(), pipeline);
    var step2 = mockStep("step2", step1, pipeline);

    var runnerSpy = Mockito.spy(new PipelineRunnerStub<>(pipeline));

    runnerSpy.start(new Object());

    @SuppressWarnings("unchecked")
    Artifact<Object> artifact1 = (Artifact<Object>) step1.provides().stream().findAny()
        .orElseThrow();
    @SuppressWarnings("unchecked")
    Artifact<Object> artifact2 = (Artifact<Object>) step2.provides().stream().findAny()
        .orElseThrow();
    runnerSpy.completeArtifact(artifact1, new Object());
    runnerSpy.completeArtifact(artifact2, new Object());

    assertThat(runnerSpy.state()).isEqualTo(PipelineState.COMPLETED);
    verify(runnerSpy, never()).handlePipelineFailed();
    verify(runnerSpy, times(1)).handlePipelineCompleted();
  }

  @Test
  void test_runner_start_running_pipeline_idempotent() {
    var pipeline = mockPipeline("pipeline");
    mockStep("step1", pipeline.rootStep(), pipeline);
    var runner = new PipelineRunnerStub<>(pipeline);
    assertThat(runner.start(new Object())).isTrue();
    assertThat(runner.start(new Object())).isFalse();
  }

  @Test
  void test_runner_product_complete_idempotent() {
    var pipeline = mockPipeline("pipeline");
    var step1 = mockStep("step1", pipeline.rootStep(), pipeline);
    mockStep("step2", pipeline.rootStep(), pipeline);
    var runner = new PipelineRunnerStub<>(pipeline);
    runner.start(new Object());

    @SuppressWarnings("unchecked")
    Artifact<Object> artifact = (Artifact<Object>) step1.provides().stream().findAny()
        .orElseThrow();

    assertThat(runner.completeArtifact(artifact, new Object())).isTrue();
    assertThat(runner.completeArtifact(artifact, new Object())).isFalse();
  }

  @Test
  void test_runner_product_fail_idempotent() {
    var pipeline = mockPipeline("pipeline");
    var step1 = mockStep("step1", pipeline.rootStep(), pipeline);
    mockStep("step2", pipeline.rootStep(), pipeline);
    var runner = new PipelineRunnerStub<>(pipeline);
    runner.start(new Object());

    @SuppressWarnings("unchecked")
    Artifact<Object> artifact = (Artifact<Object>) step1.provides().stream().findAny()
        .orElseThrow();

    assertThat(runner.failArtifact(artifact, new RuntimeException())).isTrue();
    assertThat(runner.failArtifact(artifact, new RuntimeException())).isFalse();
  }

  @Test
  void test_runner_dont_complete_when_not_running() {
    var pipeline = mockPipeline("pipeline");
    var runner = new PipelineRunnerStub<>(pipeline);
    assertThat(runner.completeArtifact(pipeline.rootStep().rootArtifact(), new Object())).isFalse();
  }

  @Test
  void test_runner_dont_fail_when_not_running() {
    var pipeline = mockPipeline("pipeline");
    var runner = new PipelineRunnerStub<>(pipeline);
    assertThat(
        runner.failArtifact(pipeline.rootStep().rootArtifact(), new RuntimeException())).isFalse();
  }

  @Test
  void test_runner_complete_reject_unknown_artifact_on() {
    var pipeline = mockPipeline("pipeline");
    mockStep("step1", pipeline.rootStep(), pipeline);
    var runner = new PipelineRunnerStub<>(pipeline);
    runner.start(new Object());

    var mockStep = mock(PipelineStep.class);
    var mockArtifact = (Artifact<Object>) mock(Artifact.class);
    when(mockArtifact.step()).thenReturn(mockStep);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> runner.completeArtifact(mockArtifact, new Object()));
  }

  @Test
  void test_runner_fail_reject_unknown_artifact_on() {
    var pipeline = mockPipeline("pipeline");
    mockStep("step1", pipeline.rootStep(), pipeline);
    var runner = new PipelineRunnerStub<>(pipeline);
    runner.start(new Object());

    var mockStep = mock(PipelineStep.class);
    var mockArtifact = (Artifact<Object>) mock(Artifact.class);
    when(mockArtifact.step()).thenReturn(mockStep);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> runner.failArtifact(mockArtifact, new RuntimeException()));
  }



  private static Pipeline<Object> mockPipeline(String name) {
    Pipeline<Object> pipeline = mock(Pipeline.class, name);
    List<PipelineStep> steps = new ArrayList<>();
    when(pipeline.inputType()).thenReturn(TypeToken.of(Object.class));
    when(pipeline.steps()).thenReturn(steps);
    when(pipeline.name()).thenReturn(name);
    doAnswer(i -> steps.add(i.getArgument(0)))
        .when(pipeline).registerStep(any());

    doAnswer(i -> steps.addAll(i.getArgument(0)))
        .when(pipeline).registerSteps(any());

    var rootStep = new RootPipelineStep<>(pipeline);
    when(pipeline.rootStep()).thenReturn(rootStep);

    pipeline.registerStep(rootStep);

    return pipeline;
  }

  private static PipelineStep mockStep(String name, PipelineStep dependency,
      Pipeline<Object> pipeline) {
    PipelineStep step = mock(PipelineStep.class, name);
    when(step.name()).thenReturn(name);

    Artifact<Object> artifact = mock(Artifact.class, "artifact-" + name);
    var key = new ResourceKeyImpl<>(TypeToken.of(Object.class));
    when(artifact.key()).thenReturn(key);
    when(artifact.step()).thenReturn(step);

    var provides = dependency.provides();
    when(step.dependencies()).thenReturn(provides);
    when(step.dependencySteps()).thenReturn(List.of(dependency));
    when(step.provides()).thenReturn(List.of(artifact));
    when(step.provided(any()))
        .thenAnswer(i ->
            Optional.of(i.getArgument(0, ResourceKey.class))
                .filter(k -> artifact.key().equals(k))
                .map(_ -> artifact));

    pipeline.registerStep(step);
    return step;
  }
}