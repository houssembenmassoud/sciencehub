package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.Document;
import io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineContext;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineStep;
import io.qzz.tbsciencehubproject.resource.ResourceKey;
import io.qzz.tbsciencehubproject.user.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Pipeline step that signs a document when executed.
 * Automatically triggered when all dependencies are satisfied.
 */
public class SignDocumentStep extends AbstractPipelineStep {
    
    private final String signerName;
    private final Set<Artifact<?>> providedArtifacts = new CopyOnWriteArraySet<>();
    
    // Resource keys for artifacts
    public static final ResourceKey<Document> DOCUMENT_KEY = 
        ResourceKey.of("signedDocument", Document.class);
    public static final ResourceKey<Boolean> SIGNATURE_COMPLETE_KEY = 
        ResourceKey.of("signatureComplete", Boolean.class);
    
    public SignDocumentStep(String signerName) {
        super("SignDocument_" + signerName);
        this.signerName = signerName;
        
        // Register provided artifacts
        providedArtifacts.add(new ArtifactImpl<>(DOCUMENT_KEY, this));
        providedArtifacts.add(new ArtifactImpl<>(SIGNATURE_COMPLETE_KEY, this));
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
        // Will be populated by dependOn() calls
        return dependencies().stream()
            .map(Artifact::step)
            .toList();
    }
    
    /**
     * Execute the signing operation.
     * Called by PipelineRunner when all dependencies are satisfied.
     */
    public void execute(PipelineContext context, Document document, User signer) {
        try {
            // Sign the document
            document.sign(signer);
            
            // Complete artifacts to trigger next steps
            context.completeArtifact(DOCUMENT_KEY, document);
            context.completeArtifact(SIGNATURE_COMPLETE_KEY, true);
            
        } catch (Exception e) {
            context.failArtifact(SIGNATURE_COMPLETE_KEY, e);
        }
    }
    
    public String getSignerName() {
        return signerName;
    }
}
