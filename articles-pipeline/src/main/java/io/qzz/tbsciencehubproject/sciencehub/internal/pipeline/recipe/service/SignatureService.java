package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service;

import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.SignatureMetadata;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.SignatureMetadataRepository;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.storage.ArticleDocumentRepository;
import io.qzz.tbsciencehubproject.user.User;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.Security;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class SignatureService {
    
    private final SignatureMetadataRepository repository;
    private final HashService hashService;
    private final CertificateService certificateService;
    private final ArticleDocumentRepository articleDocumentRepository;
    
    static {
        // Register BouncyCastle provider for cryptographic operations
        Security.addProvider(new BouncyCastleProvider());
    }
    
    public SignatureService(SignatureMetadataRepository repository, 
                           HashService hashService, 
                           CertificateService certificateService,
                           ArticleDocumentRepository articleDocumentRepository) {
        this.repository = repository;
        this.hashService = hashService;
        this.certificateService = certificateService;
        this.articleDocumentRepository = articleDocumentRepository;
    }
    
    /**
     * Record a signature on a document with full PKI cryptographic signing.
     */
    public SignatureMetadata signDocument(String articleId, User user, byte[] documentContents, String reason) {
        // Validate article exists
        if (!articleDocumentRepository.existsById(articleId)) {
            throw new IllegalArgumentException("Article not found: " + articleId);
        }
        
        // Check if user already signed
        if (repository.existsByArticleIdAndUserId(articleId, user.name())) {
            throw new IllegalStateException("User " + user.name() + " has already signed this article");
        }
        
        // Compute document hash
        String documentHash = hashService.sha256(documentContents);
        
        // Generate cryptographic signature using user's private key and certificate
        String signatureValue;
        try {
            byte[] signatureBytes = certificateService.signData(user.name(), documentContents);
            signatureValue = Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign document for user: " + user.name(), e);
        }
        
        // Create signature metadata
        SignatureMetadata metadata = new SignatureMetadata();
        metadata.setArticleId(articleId);
        metadata.setUserId(user.name());  // Use user.name() as identifier
        metadata.setDocumentHash(documentHash);
        metadata.setSignatureValue(signatureValue);
        metadata.setReason(reason != null ? reason : "Article approval");
        
        return repository.save(metadata);
    }
    
    /**
     * Sign document by article ID only (fetches contents from storage)
     */
    public SignatureMetadata signDocument(String articleId, User user, String reason) {
        byte[] documentContents = articleDocumentRepository.findDocumentContents(articleId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found for article: " + articleId));
        
        return signDocument(articleId, user, documentContents, reason);
    }
    
    /**
     * Verify all signatures for an article using PKI
     */
    public boolean verifySignatures(String articleId, byte[] currentDocumentContents) {
        var signatures = repository.findByArticleIdOrderBySignedAtAsc(articleId);
        
        if (signatures.isEmpty()) {
            return false;
        }
        
        String currentHash = hashService.sha256(currentDocumentContents);
        
        // Verify each signature cryptographically
        for (SignatureMetadata sig : signatures) {
            // Verify document hasn't changed
            if (!sig.getDocumentHash().equals(currentHash)) {
                return false;
            }
            
            // Verify cryptographic signature
            if (sig.getSignatureValue() != null && !sig.getSignatureValue().isEmpty()) {
                try {
                    byte[] signatureBytes = Base64.getDecoder().decode(sig.getSignatureValue());
                    boolean isValid = certificateService.verifySignature(sig.getUserId(), currentDocumentContents, signatureBytes);
                    if (!isValid) {
                        return false;
                    }
                } catch (Exception e) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Verify signatures by fetching document from storage
     */
    public boolean verifySignatures(String articleId) {
        byte[] documentContents = articleDocumentRepository.findDocumentContents(articleId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found for article: " + articleId));
        
        return verifySignatures(articleId, documentContents);
    }
    
    /**
     * Get all signatures for an article
     */
    public List<SignatureMetadata> getSignatures(String articleId) {
        return repository.findByArticleIdOrderBySignedAtAsc(articleId);
    }
    
    /**
     * Count signatures for an article
     */
    public long countSignatures(String articleId) {
        return repository.findByArticleIdOrderBySignedAtAsc(articleId).size();
    }
    
    /**
     * Verify a specific signature
     */
    public boolean verifySingleSignature(String userId, byte[] documentContents, String signatureBase64) {
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            return certificateService.verifySignature(userId, documentContents, signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }
}
