package io.qzz.tbsciencehubproject.pipeline.validate;

import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineStep;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

public interface PipelineValidator {

  void validate(Graph<PipelineStep, DefaultEdge> buildGraph) throws PipelineValidationException;

}
