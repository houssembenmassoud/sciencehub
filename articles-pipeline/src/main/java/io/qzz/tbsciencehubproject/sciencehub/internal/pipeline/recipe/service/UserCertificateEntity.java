package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA Entity for storing user certificates and keys in PostgreSQL.
 * Provides persistent storage for PKI infrastructure.
 */
@Entity
@Table(name = "user_certificates")
public class UserCertificateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false, length = 255)
    private String userId;

    @Column(name = "user_name", nullable = false, length = 500)
    private String userName;

    @Column(name = "private_key_data", nullable = false, columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String privateKeyData;  // PEM-encoded private key

    @Column(name = "certificate_data", nullable = false, columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String certificateData;  // PEM-encoded X.509 certificate

    @Column(name = "public_key_data", nullable = false, columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String publicKeyData;  // PEM-encoded public key

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.version == null) {
            this.version = 1;
        }
    }

    // Constructors
    public UserCertificateEntity() {
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getPrivateKeyData() { return privateKeyData; }
    public void setPrivateKeyData(String privateKeyData) { this.privateKeyData = privateKeyData; }

    public String getCertificateData() { return certificateData; }
    public void setCertificateData(String certificateData) { this.certificateData = certificateData; }

    public String getPublicKeyData() { return publicKeyData; }
    public void setPublicKeyData(String publicKeyData) { this.publicKeyData = publicKeyData; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
