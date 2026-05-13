package io.qzz.tbsciencehubproject.pipeline.pipeline;

import io.qzz.tbsciencehubproject.pipeline.validate.PipelineValidationException;
import io.qzz.tbsciencehubproject.pipeline.validate.PipelineValidator;
import io.qzz.tbsciencehubproject.utils.TypeToken;
import java.util.Collection;

public interface Pipeline<I> {

  String name();

  void registerValidator(PipelineValidator validator);

  void validate() throws PipelineValidationException;

  void registerStep(PipelineStep step);

  void registerSteps(Collection<? extends PipelineStep> steps);

  Collection<PipelineStep> steps();

  RootPipelineStep<I> rootStep();

  TypeToken<I> inputType();

}
