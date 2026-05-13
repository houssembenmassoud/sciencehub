package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SignatureMetadataRepository extends JpaRepository<SignatureMetadata, UUID> {
    List<SignatureMetadata> findByArticleIdOrderBySignedAtAsc(String articleId);
    boolean existsByArticleIdAndUserId(String articleId, String userId);
}
