package io.qzz.tbsciencehubproject.app.controller;

import io.qzz.tbsciencehubproject.app.config.SecurityConfig;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.SignatureMetadata;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.storage.ArticleDocumentRepository;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service.CertificateService;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service.SignatureService;
import io.qzz.tbsciencehubproject.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for e-signing operations.
 * Integrates with existing authentication - uses SecurityConfig to get current user.
 */
@RestController
@RequestMapping("/api/signatures")
public class SignatureController {
    
    private final SignatureService signatureService;
    private final CertificateService certificateService;
    private final ArticleDocumentRepository articleDocumentRepository;
    
    public SignatureController(SignatureService signatureService, 
                              CertificateService certificateService,
                              ArticleDocumentRepository articleDocumentRepository) {
        this.signatureService = signatureService;
        this.certificateService = certificateService;
        this.articleDocumentRepository = articleDocumentRepository;
    }
    
    /**
     * Sign an article/document
     * POST /api/signatures/sign/{articleId}
     */
    @PostMapping("/sign/{articleId}")
    public ResponseEntity<Map<String, Object>> signDocument(
            @PathVariable String articleId,
            @RequestBody(required = false) Map<String, String> requestBody) {
        
        try {
            // Get current authenticated user from existing auth system
            User currentUser = SecurityConfig.getCurrentUser();
            
            // Get document contents (in real scenario, fetch from storage)
            byte[] documentContents = fetchDocumentContents(articleId);
            
            // Extract reason if provided
            String reason = requestBody != null ? requestBody.get("reason") : null;
            
            // Sign the document
            SignatureMetadata signature = signatureService.signDocument(
                articleId, 
                currentUser, 
                documentContents, 
                reason
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("signatureId", signature.getId());
            response.put("signedAt", signature.getSignedAt());
            response.put("userId", signature.getUserId());
            response.put("message", "Document signed successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            // User not authenticated
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Authentication required");
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
     * Verify signatures on an article
     * GET /api/signatures/verify/{articleId}
     */
    @GetMapping("/verify/{articleId}")
    public ResponseEntity<Map<String, Object>> verifySignatures(@PathVariable String articleId) {
        try {
            byte[] documentContents = fetchDocumentContents(articleId);
            boolean isValid = signatureService.verifySignatures(articleId, documentContents);
            List<SignatureMetadata> signatures = signatureService.getSignatures(articleId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            response.put("signatureCount", signatures.size());
            response.put("signatures", signatures);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("valid", false);
            error.put("error", "Verification failed");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Get all signatures for an article
     * GET /api/signatures/{articleId}
     */
    @GetMapping("/{articleId}")
    public ResponseEntity<List<SignatureMetadata>> getSignatures(@PathVariable String articleId) {
        List<SignatureMetadata> signatures = signatureService.getSignatures(articleId);
        return ResponseEntity.ok(signatures);
    }
    
    /**
     * Export user certificate
     * GET /api/signatures/certificate/export
     */
    @GetMapping("/certificate/export")
    public ResponseEntity<Map<String, String>> exportCertificate() {
        try {
            User currentUser = SecurityConfig.getCurrentUser();
            String certificatePem = certificateService.exportCertificate(currentUser.name(), currentUser.name());
            
            Map<String, String> response = new HashMap<>();
            response.put("certificate", certificatePem);
            response.put("userId", currentUser.name());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Certificate export failed");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Verify a single signature
     * POST /api/signatures/verify-single
     */
    @PostMapping("/verify-single")
    public ResponseEntity<Map<String, Object>> verifySingleSignature(
            @RequestBody Map<String, Object> requestBody) {
        
        try {
            String userId = (String) requestBody.get("userId");
            String documentId = (String) requestBody.get("documentId");
            String signatureBase64 = (String) requestBody.get("signature");
            
            byte[] documentContents = fetchDocumentContents(documentId);
            boolean isValid = signatureService.verifySingleSignature(userId, documentContents, signatureBase64);
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            response.put("userId", userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("valid", false);
            error.put("error", "Verification failed");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Helper method to fetch document contents from storage
     */
    private byte[] fetchDocumentContents(String articleId) {
        return articleDocumentRepository.findDocumentContents(articleId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found for article: " + articleId));
    }
}
