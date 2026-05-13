package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.storage;

import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.storage.entity.ArticleDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA Repository for article documents stored in PostgreSQL.
 * Provides CRUD operations for article documents with binary content.
 */
@Repository
public interface ArticleDocumentJpaRepository extends JpaRepository<ArticleDocumentEntity, Long> {
    
    /**
     * Find document by article ID (business identifier)
     */
    Optional<ArticleDocumentEntity> findByArticleId(String articleId);
    
    /**
     * Check if document exists by article ID
     */
    boolean existsByArticleId(String articleId);
    
    /**
     * Find all documents by status
     */
    java.util.List<ArticleDocumentEntity> findByStatus(String status);
    
    /**
     * Update document status by article ID
     */
    @Query("UPDATE ArticleDocumentEntity d SET d.status = :status, d.updatedAt = CURRENT_TIMESTAMP WHERE d.articleId = :articleId")
    int updateStatusByArticleId(@Param("articleId") String articleId, @Param("status") String status);
    
    /**
     * Delete document by article ID
     */
    void deleteByArticleId(String articleId);
}
