package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA Repository for user certificates stored in PostgreSQL.
 */
@Repository
public interface UserCertificateRepository extends JpaRepository<UserCertificateEntity, Long> {
    
    /**
     * Find certificate by user ID
     */
    Optional<UserCertificateEntity> findByUserId(String userId);
    
    /**
     * Check if certificate exists for user
     */
    boolean existsByUserId(String userId);
    
    /**
     * Find active certificate by user ID
     */
    @Query("SELECT c FROM UserCertificateEntity c WHERE c.userId = :userId AND c.isActive = true")
    Optional<UserCertificateEntity> findActiveByUserId(@Param("userId") String userId);
    
    /**
     * Update certificate status
     */
    @Query("UPDATE UserCertificateEntity c SET c.isActive = :active WHERE c.userId = :userId")
    int updateActiveStatus(@Param("userId") String userId, @Param("active") boolean active);
}
