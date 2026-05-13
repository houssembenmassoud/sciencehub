package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineStep;
import io.qzz.tbsciencehubproject.resource.ResourceKey;

public record ArtifactImpl<T>(ResourceKey<T> key, PipelineStep step) implements Artifact<T> {
}
