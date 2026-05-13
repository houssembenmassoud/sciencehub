package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.storage;

import java.util.Optional;

/**
 * Repository interface for article document storage.
 * Integrates with existing article storage system - no duplication.
 * 
 * This is a placeholder interface that should be implemented by your
 * existing article repository or storage service.
 */
public interface ArticleDocumentRepository {
    
    /**
     * Retrieve article document contents by article ID
     * 
     * @param articleId the unique identifier of the article
     * @return Optional containing document bytes if found
     */
    Optional<byte[]> findDocumentContents(String articleId);
    
    /**
     * Retrieve article filename by article ID
     * 
     * @param articleId the unique identifier of the article
     * @return Optional containing filename if found
     */
    Optional<String> findFilename(String articleId);
    
    /**
     * Check if an article exists
     * 
     * @param articleId the unique identifier of the article
     * @return true if article exists, false otherwise
     */
    boolean existsById(String articleId);
    
    /**
     * Update article status after successful signing
     * 
     * @param articleId the unique identifier of the article
     * @param status the new status (e.g., "APPROVED", "SIGNED")
     */
    void updateStatus(String articleId, String status);
}
