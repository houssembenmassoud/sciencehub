package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.storage;

import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.storage.entity.ArticleDocumentEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * PostgreSQL-backed implementation of ArticleDocumentRepository.
 * Stores documents directly in the database as BYTEA (binary data).
 * No file system storage - fully monolithic architecture.
 */
@Service
@Transactional
public class PostgresArticleDocumentRepository implements ArticleDocumentRepository {

    private final ArticleDocumentJpaRepository jpaRepository;

    @Autowired
    public PostgresArticleDocumentRepository(ArticleDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<byte[]> findDocumentContents(String articleId) {
        return jpaRepository.findByArticleId(articleId)
                .map(ArticleDocumentEntity::getDocumentData);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findFilename(String articleId) {
        return jpaRepository.findByArticleId(articleId)
                .map(ArticleDocumentEntity::getFilename);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(String articleId) {
        return jpaRepository.existsByArticleId(articleId);
    }

    @Override
    public void updateStatus(String articleId, String status) {
        int updated = jpaRepository.updateStatusByArticleId(articleId, status);
        if (updated == 0) {
            throw new IllegalStateException("Article not found: " + articleId);
        }
    }

    /**
     * Save or update an article document in PostgreSQL.
     * 
     * @param articleId the unique identifier of the article
     * @param filename the original filename
     * @param contentType MIME type of the document
     * @param content binary content of the document
     * @return saved entity
     */
    public ArticleDocumentEntity saveDocument(String articleId, String filename, 
                                               String contentType, byte[] content) {
        ArticleDocumentEntity entity = new ArticleDocumentEntity();
        entity.setArticleId(articleId);
        entity.setFilename(filename);
        entity.setContentType(contentType);
        entity.setDocumentContent(content);
        entity.setStatus("PENDING");
        
        return jpaRepository.save(entity);
    }

    /**
     * Get document entity with metadata.
     * 
     * @param articleId the unique identifier of the article
     * @return optional containing the entity if found
     */
    @Transactional(readOnly = true)
    public Optional<ArticleDocumentEntity> findEntityByArticleId(String articleId) {
        return jpaRepository.findByArticleId(articleId);
    }

    /**
     * Get document size without loading full content.
     * 
     * @param articleId the unique identifier of the article
     * @return optional containing file size if found
     */
    @Transactional(readOnly = true)
    public Optional<Long> getFileSize(String articleId) {
        return jpaRepository.findByArticleId(articleId)
                .map(ArticleDocumentEntity::getFileSize);
    }

    /**
     * Get document content type.
     * 
     * @param articleId the unique identifier of the article
     * @return optional containing content type if found
     */
    @Transactional(readOnly = true)
    public Optional<String> getContentType(String articleId) {
        return jpaRepository.findByArticleId(articleId)
                .map(ArticleDocumentEntity::getContentType);
    }

    /**
     * Delete document by article ID.
     * 
     * @param articleId the unique identifier of the article
     */
    public void deleteByArticleId(String articleId) {
        jpaRepository.deleteByArticleId(articleId);
    }
}
