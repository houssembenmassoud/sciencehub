package io.qzz.tbsciencehubproject.pipeline.pipeline;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

public abstract class PipelineRunner<T> {

  private final Set<Stage> activeStages = new HashSet<>();
  private final Queue<Stage> remainingStages;
  private final SimpleDirectedGraph<Stage, DefaultEdge> graph;
  private volatile PipelineState runningState = PipelineState.IDLE;
  protected final Map<PipelineStep, Stage> stageMap;
  private final Pipeline<T> pipeline;

  public PipelineRunner(Pipeline<T> pipeline) {
    Objects.requireNonNull(pipeline, "pipeline");

    var rootStep = Objects.requireNonNull(pipeline.rootStep(), "rootStep");
    var registeredSteps = Objects.requireNonNull(pipeline.steps(), "steps");
    SimpleDirectedGraph<Stage, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);
    if (!registeredSteps.contains(rootStep)) {
      throw new IllegalArgumentException("Root step must be a registered step!");
    }

    var workerMap = new HashMap<PipelineStep, Stage>();
    for (var step : registeredSteps) {
      workerMap.put(step, Stage.ofStep(step));
    }

    for (PipelineStep step : registeredSteps) {
      var stage = workerMap.get(step);
      graph.addVertex(stage);
    }

    for (PipelineStep step : registeredSteps) {
      var stage = workerMap.get(step);
      for (var dep : step.dependencySteps()) {
        var depStage = Objects.requireNonNull(workerMap.get(dep),
            "Step depends on step outside build graph");
        graph.addEdge(depStage, stage);
      }
    }

    Iterable<Stage> topologicalSort = () -> new TopologicalOrderIterator<>(graph);

    this.remainingStages = StreamSupport.stream(topologicalSort.spliterator(), false)
        .collect(Collectors.toCollection(ArrayDeque::new));
    this.stageMap = workerMap;
    this.graph = graph;
    this.pipeline = pipeline;
  }

  public synchronized PipelineState state() {
    return runningState;
  }

  public enum PipelineState {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED;
  }

  protected abstract void handlePipelineCompleted();

  protected abstract void handleStepSucceeded(Stage stage);

  protected abstract void handlePipelineFailed();

  protected abstract void handleStepStarted(Stage stage);

  private synchronized void setState(PipelineState state) {
    this.runningState = state;
  }

  public Collection<Stage> queryActiveSteps() {
    return Set.copyOf(activeStages);
  }

  public Collection<Stage> queryRemainingSteps() {
    return Set.copyOf(remainingStages);
  }

  public Collection<Stage> queryCompletedSteps() {
    return graph.vertexSet()
        .stream().filter(Stage::completed)
        .toList();
  }

  public synchronized <V> boolean completeArtifact(Artifact<V> artifact, V value) {
    Objects.requireNonNull(artifact, "artifact");

    if (runningState != PipelineState.RUNNING)
      return false;

    var stage = stageMap.get(artifact.step());
    if (stage == null) {
      throw new IllegalArgumentException("Artifact is not on the build graph");
    }
    var product = stage.product(artifact);
    return product.complete(value) && evaluate();
  }

  public synchronized boolean failArtifact(Artifact<?> artifact, Throwable error) {
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(error, "error");

    if (runningState != PipelineState.RUNNING)
      return false;

    var stage = stageMap.get(artifact.step());
    if (stage == null) {
      throw new IllegalArgumentException("Artifact is not on the build graph");
    }
    var product = stage.product(artifact);
    return product.fail(error) && evaluate();
  }

  public final synchronized boolean start(T startValue) {
    Objects.requireNonNull(startValue, "startValue");
    if (runningState != PipelineState.IDLE) {
      return false;
    }

    var step = pipeline.rootStep();
    var artifact = step.rootArtifact();
    var stage = fromStep(step);

    setState(PipelineState.RUNNING);
    startStep(stage);
    remainingStages.remove(stage);
    return stage.product(artifact).complete(startValue) && evaluate();
  }

    public final boolean run(Pipeline<T> pipeline, T startValue) {
    return start(startValue);
  }
  private synchronized boolean evaluate() {
    if (runningState != PipelineState.RUNNING) {
      return false;
    }

    var queue = new ArrayDeque<>(activeStages);
    while (!queue.isEmpty()) {
      var step = queue.remove();
      if (step.completed()) {
        if (step.succeeded()) {
          activeStages.remove(step);
          handleStepSucceeded(step);
        } else if (step.failed()) {
          activeStages.clear();
          remainingStages.clear();
          setState(PipelineState.FAILED);
          handlePipelineFailed();
          return true;
        }
      }
    }

    boolean satisfied;
    do {
      if (remainingStages.isEmpty()) {
        if (activeStages.isEmpty()) {
          setState(PipelineState.COMPLETED);
          handlePipelineCompleted();
        }
        break;
      }
      satisfied = this.graph.incomingEdgesOf(remainingStages.element())
          .stream().map(graph::getEdgeSource)
          .allMatch(Stage::completed);

      if (!satisfied) {
        break;
      }

      var step = remainingStages.remove();
      startStep(step);

    } while (true);
    return true;
  }

  private void startStep(Stage stage) {
    activeStages.add(stage);
    handleStepStarted(stage);
  }

  private Stage fromStep(PipelineStep step) {
    return stageMap.get(step);
  }

  public record Stage(Map<? extends Artifact<?>, ? extends Product<?>> products,
                      PipelineStep step) {

    public boolean completed() {
      return products.values().stream().allMatch(Product::completed);
    }

    public boolean failed() {
      return products.values().stream().allMatch(Product::failed);
    }

    public boolean succeeded() {
      return products.values().stream().allMatch(Product::succeeded);
    }

    private static Stage ofStep(PipelineStep step) {
      var deps = step.provides()
          .stream().map(a -> Map.entry(a, new ProductImpl<>(a)))
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
      return new Stage(deps, step);
    }

    public <T> Product<T> product(Artifact<T> artifact) {
      //noinspection unchecked
      return (Product<T>) products.get(artifact);
    }
  }

}
