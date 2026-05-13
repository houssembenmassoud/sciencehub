package io.qzz.tbsciencehubproject.app.controller;

import io.qzz.tbsciencehubproject.app.config.SecurityConfig;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.ApproveArticlePipeline;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service.SignaturePipelineService;
import io.qzz.tbsciencehubproject.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing e-signature pipeline execution.
 */
@RestController
@RequestMapping("/api/signatures/pipeline")
public class SignaturePipelineController {
    
    private final SignaturePipelineService pipelineService;
    
    public SignaturePipelineController(SignaturePipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }
    
    /**
     * Create a signing pipeline for an article
     * POST /api/signatures/pipeline/create/{articleId}
     */
    @PostMapping("/create/{articleId}")
    public ResponseEntity<Map<String, Object>> createPipeline(
            @PathVariable String articleId,
            @RequestBody Map<String, Object> requestBody) {
        
        try {
            List<String> signerNames = (List<String>) requestBody.get("signers");
            Integer requiredSignatures = (Integer) requestBody.get("requiredSignatures");
            
            if (signerNames == null || signerNames.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Signers list is required"
                ));
            }
            
            int requiredCount = requiredSignatures != null ? requiredSignatures : signerNames.size();
            
            ApproveArticlePipeline pipeline = pipelineService.createSigningPipeline(
                articleId, signerNames, requiredCount
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("pipelineName", pipeline.name());
            response.put("stepCount", pipeline.steps().size());
            response.put("message", "Signing pipeline created successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Article not found");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Pipeline creation failed");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Start pipeline execution
     * POST /api/signatures/pipeline/start/{articleId}
     */
    @PostMapping("/start/{articleId}")
    public ResponseEntity<Map<String, Object>> startPipeline(@PathVariable String articleId) {
        try {
            pipelineService.startPipeline(articleId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Pipeline execution started");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Pipeline not found");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Pipeline start failed");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Sign document through pipeline (for current user)
     * POST /api/signatures/pipeline/sign/{articleId}
     */
    @PostMapping("/sign/{articleId}")
    public ResponseEntity<Map<String, Object>> signThroughPipeline(@PathVariable String articleId) {
        try {
            User currentUser = SecurityConfig.getCurrentUser();
            pipelineService.executeSigningStep(articleId, currentUser);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", currentUser.name());
            response.put("message", "Signature step executed");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Authentication or pipeline error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(401).body(error);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Signing failed");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Check pipeline status
     * GET /api/signatures/pipeline/status/{articleId}
     */
    @GetMapping("/status/{articleId}")
    public ResponseEntity<Map<String, Object>> getPipelineStatus(@PathVariable String articleId) {
        ApproveArticlePipeline pipeline = pipelineService.getPipeline(articleId);
        
        if (pipeline == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("exists", false);
            error.put("message", "No active pipeline for this article");
            return ResponseEntity.ok(error);
        }
        
        boolean isComplete = pipelineService.isPipelineComplete(articleId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("exists", true);
        response.put("name", pipeline.name());
        response.put("stepCount", pipeline.steps().size());
        response.put("complete", isComplete);
        
        return ResponseEntity.ok(response);
    }
}
