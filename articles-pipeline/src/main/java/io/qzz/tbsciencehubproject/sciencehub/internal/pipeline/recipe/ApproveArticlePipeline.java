package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Pipeline;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineStep;
import io.qzz.tbsciencehubproject.pipeline.pipeline.RootPipelineStep;
import io.qzz.tbsciencehubproject.pipeline.validate.PipelineValidationException;
import io.qzz.tbsciencehubproject.pipeline.validate.PipelineValidator;
import io.qzz.tbsciencehubproject.utils.TypeToken;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ApproveArticlePipeline implements Pipeline<Void> {

  private final String recipeName;
  private final int requiredSignatures;
  private final Set<PipelineValidator> pipelineValidators = new CopyOnWriteArraySet<>();
  private final Set<PipelineStep> steps = new CopyOnWriteArraySet<>();
  private RootPipelineStep<Void> rootStep;

  public ApproveArticlePipeline(String recipeName) {
    this(recipeName, 2); // Default: 2 signatures required
  }
  
  public ApproveArticlePipeline(String recipeName, int requiredSignatures) {
    this.recipeName = recipeName;
    this.requiredSignatures = requiredSignatures;
  }

  @Override
  public String name() {
    return recipeName;
  }

  @Override
  public void registerValidator(PipelineValidator validator) {
    this.pipelineValidators.add(validator);
  }

  @Override
  public void validate() throws PipelineValidationException {
    for (PipelineValidator validator : pipelineValidators) {
      validator.validate(null);
    }
    // Validate that we have required signing steps
    long signSteps = steps.stream()
        .filter(s -> s instanceof SignDocumentStep)
        .count();
    
    if (signSteps < requiredSignatures) {
      throw new PipelineValidationException(
          "Pipeline requires at least " + requiredSignatures + " signing steps");
    }
  }

  @Override
  public void registerStep(PipelineStep step) {
    steps.add(step);
    if (step instanceof RootPipelineStep) {
      this.rootStep = (RootPipelineStep<Void>) step;
    }
  }

  @Override
  public void registerSteps(Collection<? extends PipelineStep> steps) {
    this.steps.addAll(steps);
    for (PipelineStep step : steps) {
      if (step instanceof RootPipelineStep) {
        this.rootStep = (RootPipelineStep<Void>) step;
      }
    }
  }

  @Override
  public Collection<PipelineStep> steps() {
    return List.copyOf(steps);
  }

  @Override
  public RootPipelineStep<Void> rootStep() {
    return rootStep;
  }

  @Override
  public TypeToken<Void> inputType() {
    return TypeToken.of(Void.class);
  }
  
  /**
   * Helper method to configure standard approval pipeline with signing
   */
  public static ApproveArticlePipeline createWithSigning(
      String recipeName, 
      List<String> signerNames,
      int requiredSignatureCount) {
      
    ApproveArticlePipeline pipeline = new ApproveArticlePipeline(recipeName, requiredSignatureCount);
    
    // Create root step
    RootPipelineStep<Void> rootStep = new RootPipelineStep<>(pipeline);
    pipeline.registerStep(rootStep);
    
    // Create signing steps for each signer
    SignDocumentStep[] signSteps = new SignDocumentStep[signerNames.size()];
    for (int i = 0; i < signerNames.size(); i++) {
      signSteps[i] = new SignDocumentStep(signerNames.get(i));
      
      // Each signing step depends on previous step (or root for first)
      if (i == 0) {
        signSteps[i].dependOn(rootStep.rootArtifact());
      } else {
        signSteps[i].dependOn(signSteps[i-1].provides().iterator().next());
      }
      
      pipeline.registerStep(signSteps[i]);
    }
    
    // Add verification step after all signing
    VerifySignaturesStep verifyStep = new VerifySignaturesStep(requiredSignatureCount);
    verifyStep.dependOn(signSteps[signSteps.length - 1].provides().iterator().next());
    pipeline.registerStep(verifyStep);
    
    return pipeline;
  }
}
