package io.qzz.tbsciencehubproject.pipeline.pipeline;

import io.qzz.tbsciencehubproject.resource.ResourceKey;
import java.util.Map;
import java.util.Optional;

public interface PipelineContext {

  Pipeline pipeline();

  <T> Optional<T> resource(ResourceKey<T> key);

  void copyResourcesTo(Map<ResourceKey<?>, Object> resources);
  
  <T> void completeArtifact(ResourceKey<T> key, T value);

  <T> void failArtifact(ResourceKey<T> key, Throwable cause);
  
  default <T> void completeArtifact(Artifact<T> artifact, T value) {
    completeArtifact(artifact.key(), value);
  }

  default <T> void failArtifact(Artifact<T> artifact, Throwable cause) {
    failArtifact(artifact.key(), cause);
  }

}
