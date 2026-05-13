package io.qzz.tbsciencehubproject.pipeline.pipeline;

import io.qzz.tbsciencehubproject.resource.ResourceKey;

public interface Artifact<T> {

  ResourceKey<T> key();

  PipelineStep step();
}
