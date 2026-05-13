package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.Document;
import io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineContext;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineStep;
import io.qzz.tbsciencehubproject.resource.ResourceKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Pipeline step that verifies all required signatures are present.
 */
public class VerifySignaturesStep extends AbstractPipelineStep {
    
    private final int requiredSignatureCount;
    private final Set<Artifact<?>> providedArtifacts = new CopyOnWriteArraySet<>();
    
    public static final ResourceKey<Boolean> VERIFICATION_RESULT_KEY = 
        ResourceKey.of("verificationResult", Boolean.class);
    
    public VerifySignaturesStep(int requiredSignatureCount) {
        super("VerifySignatures");
        this.requiredSignatureCount = requiredSignatureCount;
        
        providedArtifacts.add(new ArtifactImpl<>(VERIFICATION_RESULT_KEY, this));
    }
    
    @Override
    public Collection<Artifact<?>> provides() {
        return List.copyOf(providedArtifacts);
    }
    
    @Override
    public <T> Optional<Artifact<?>> provided(ResourceKey<T> key) {
        for (Artifact<?> artifact : providedArtifacts) {
            if (artifact.key().equals(key)) {
                return Optional.of(artifact);
            }
        }
        return Optional.empty();
    }
    
    @Override
    public Collection<PipelineStep> dependencySteps() {
        return dependencies().stream()
            .map(Artifact::step)
            .toList();
    }
    
    /**
     * Execute verification.
     */
    public void execute(PipelineContext context, Document document) {
        try {
            boolean isValid = document.verify() && 
                             document.signatures().size() >= requiredSignatureCount;
            
                    context.completeArtifact(VERIFICATION_RESULT_KEY, isValid);

            
            if (!isValid) {
                throw new IllegalStateException(
                    "Document verification failed. Required: " + requiredSignatureCount + 
                    ", Found: " + document.signatures().size());
            }
            
        } catch (Exception e) {
                   context.failArtifact(VERIFICATION_RESULT_KEY, e);

        }
    }
}
