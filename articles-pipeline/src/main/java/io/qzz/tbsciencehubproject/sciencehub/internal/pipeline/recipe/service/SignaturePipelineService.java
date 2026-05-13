package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service;
import io.qzz.tbsciencehubproject.pipeline.validate.PipelineValidationException;

import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineRunner;
import io.qzz.tbsciencehubproject.pipeline.pipeline.RootPipelineStep;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.ApproveArticlePipeline;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.SignDocumentStep;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.VerifySignaturesStep;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.Document;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.storage.ArticleDocumentRepository;
import io.qzz.tbsciencehubproject.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing e-signature pipeline execution.
 * Handles automatic pipeline creation, execution, and article status updates.
 */
@Service
public class SignaturePipelineService {
    
    private final PipelineRunner pipelineRunner;
    private final SignatureService signatureService;
    private final ArticleDocumentRepository articleDocumentRepository;
    
    // Store active pipelines by article ID
    private final Map<String, ApproveArticlePipeline> activePipelines = new ConcurrentHashMap<>();
    
    public SignaturePipelineService(PipelineRunner pipelineRunner,
                                   SignatureService signatureService,
                                   ArticleDocumentRepository articleDocumentRepository) {
        this.pipelineRunner = pipelineRunner;
        this.signatureService = signatureService;
        this.articleDocumentRepository = articleDocumentRepository;
    }
    
    /**
     * Create and initialize a signing pipeline for an article
     */
    @Transactional
    public ApproveArticlePipeline createSigningPipeline(String articleId, List<String> signerNames, int requiredSignatures) {
        // Validate article exists
        if (!articleDocumentRepository.existsById(articleId)) {
            throw new IllegalArgumentException("Article not found: " + articleId);
        }
        
        // Create pipeline with signing steps
        ApproveArticlePipeline pipeline = ApproveArticlePipeline.createWithSigning(
            "ApprovalPipeline_" + articleId,
            signerNames,
            requiredSignatures
        );
        
        // Register pipeline validators
try {
    pipeline.validate();
} catch (PipelineValidationException e) {
    // Log the error or wrap in a runtime exception
    throw new RuntimeException("Pipeline validation failed: " + e.getMessage(), e);
}        
        // Store active pipeline
        activePipelines.put(articleId, pipeline);
        
        return pipeline;
    }
    
    /**
     * Start the signing pipeline execution
     */
    @Transactional
    public void startPipeline(String articleId) {
        ApproveArticlePipeline pipeline = activePipelines.get(articleId);
        if (pipeline == null) {
            throw new IllegalStateException("No pipeline found for article: " + articleId);
        }
        
        // Get root step and trigger execution
        RootPipelineStep<Void> rootStep = pipeline.rootStep();
        if (rootStep != null) {
            pipelineRunner.run(pipeline, null);
        }
    }
    
    /**
     * Execute a single signing step for a user
     */
    @Transactional
    public void executeSigningStep(String articleId, User signer) {
        ApproveArticlePipeline pipeline = activePipelines.get(articleId);
        if (pipeline == null) {
            throw new IllegalStateException("No pipeline found for article: " + articleId);
        }
        
        // Find the appropriate signing step for this user
        pipeline.steps().stream()
            .filter(step -> step instanceof SignDocumentStep)
            .map(step -> (SignDocumentStep) step)
            .filter(step -> step.getSignerName().equals(signer.name()))
            .findFirst()
            .ifPresentOrElse(
                step -> {
                    // Create document wrapper
                    Document document = createDocument(articleId);
                    
                    // Execute the signing step
                    // Note: In real implementation, this would be triggered by PipelineRunner
                    // when dependencies are satisfied
                    System.out.println("Executing signing step for: " + signer.name());
                },
                () -> {
                    throw new IllegalStateException("No signing step found for user: " + signer.name());
                }
            );
    }
    
    /**
     * Update article status after successful verification
     */
    @Transactional
    public void updateArticleStatus(String articleId, String status) {
        articleDocumentRepository.updateStatus(articleId, status);
        System.out.println("Article " + articleId + " status updated to: " + status);
    }
    
    /**
     * Create a Document wrapper for an article
     */
    private Document createDocument(String articleId) {
        byte[] contents = articleDocumentRepository.findDocumentContents(articleId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + articleId));
        
        String filename = articleDocumentRepository.findFilename(articleId)
            .orElse("article_" + articleId + ".pdf");
        
        return new io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.DocumentImpl(
            filename, contents, articleId, signatureService
        );
    }
    
    /**
     * Check if pipeline is complete
     */
    public boolean isPipelineComplete(String articleId) {
        ApproveArticlePipeline pipeline = activePipelines.get(articleId);
        if (pipeline == null) {
            return false;
        }
        
        // Check if verification step completed successfully
        return pipeline.steps().stream()
            .filter(step -> step instanceof VerifySignaturesStep)
            .anyMatch(step -> {
                // Check if verification artifact is complete
                // This would need access to pipeline execution state
                return true; // Placeholder
            });
    }
    
    /**
     * Get active pipeline for an article
     */
    public ApproveArticlePipeline getPipeline(String articleId) {
        return activePipelines.get(articleId);
    }
}
