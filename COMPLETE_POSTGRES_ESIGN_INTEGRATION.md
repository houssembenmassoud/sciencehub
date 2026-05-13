# Complete E-Signature Integration with PostgreSQL

## ✅ Integration Summary

This guide documents the **complete, monolithic e-signing integration** for ScienceHub with:
- **PostgreSQL database storage** for documents and certificates
- **PKI infrastructure** with persistent certificate storage
- **No entity duplication** - reuses existing User system
- **Full cryptographic signing** with RSA 2048-bit keys
- **Audit trail** with IP addresses and user agents

---

## 📁 Files Created/Modified

### Database Migrations
1. **V1__initial_schema.sql** - Initial schema with `article_documents` table
   - Stores document binary data (BYTEA) directly in PostgreSQL
   - Automatic versioning and timestamp tracking
   - Status workflow (PENDING → SIGNED → APPROVED)

2. **V2__add_signature_metadata.sql** - Enhanced signature tables
   - `article_signatures` - Signature metadata with PKI support
   - `user_certificates` - Persistent certificate/key storage
   - Audit fields (IP address, user agent)

### Entity Classes
3. **ArticleDocumentEntity.java** - JPA entity for document storage
   - Binary content stored as BYTEA
   - Optimistic locking with @Version
   - Auto-timestamps with @PrePersist/@PreUpdate

4. **UserCertificateEntity.java** - JPA entity for PKI certificates
   - PEM-encoded private/public keys
   - X.509 certificate storage
   - Expiration tracking

### Repository Layer
5. **ArticleDocumentJpaRepository.java** - Spring Data JPA repository
   - Custom query for status updates
   - Business ID lookups

6. **UserCertificateRepository.java** - Certificate persistence
   - Active certificate queries
   - User-based lookups

### Service Layer
7. **PostgresArticleDocumentRepository.java** - Document storage implementation
   - Full CRUD operations
   - Transaction management
   - Replaces placeholder implementation

8. **CertificateService.java** - Updated PKI service
   - **Persistent storage** (no more in-memory maps!)
   - Database-backed key/certificate retrieval
   - Automatic certificate generation on first use
   - PEM encoding/decoding

### Configuration
9. **application.yaml** - Already configured for PostgreSQL
   - Environment variable-based configuration
   - Flyway migration support
   - Secret management via configtree

---

## 🗄️ Database Schema

### article_documents Table
```sql
- id: UUID (primary key)
- article_id: VARCHAR(255) UNIQUE (business identifier)
- filename: VARCHAR(500)
- content_type: VARCHAR(100) (e.g., application/pdf)
- document_data: BYTEA (actual file content)
- file_size: BIGINT
- status: VARCHAR(50) (PENDING, SIGNED, APPROVED, REJECTED)
- created_at: TIMESTAMP
- updated_at: TIMESTAMP (auto-updated)
- version: INTEGER (optimistic locking)
```

### article_signatures Table
```sql
- id: UUID (primary key)
- article_id: VARCHAR(255)
- user_id: VARCHAR(255) (references existing User.name())
- document_hash: VARCHAR(64) (SHA-256)
- signature_value: TEXT (Base64-encoded cryptographic signature)
- certificate_data: TEXT (X.509 certificate used)
- signed_at: TIMESTAMP
- reason: VARCHAR(500)
- ip_address: VARCHAR(45) (audit trail)
- user_agent: VARCHAR(500) (audit trail)
```

### user_certificates Table
```sql
- id: BIGSERIAL (primary key)
- user_id: VARCHAR(255) UNIQUE
- user_name: VARCHAR(500)
- private_key_data: TEXT (PEM-encoded RSA private key)
- certificate_data: TEXT (PEM-encoded X.509 certificate)
- public_key_data: TEXT (PEM-encoded public key)
- created_at: TIMESTAMP
- expires_at: TIMESTAMP
- is_active: BOOLEAN
- version: INTEGER
```

---

## 🔐 PKI Architecture

### Certificate Authority (CA)
- Self-signed CA created at application startup
- Valid for 10 years
- Signs all user certificates

### User Certificates
- **RSA 2048-bit** key pairs
- **X.509 v3** certificates
- **1-year validity** period
- **Stored persistently** in PostgreSQL
- Auto-generated on first signing attempt

### Cryptographic Operations
- **Signing**: SHA256withRSA
- **Hashing**: SHA-256 for document integrity
- **Provider**: BouncyCastle

---

## 🚀 Usage Examples

### Upload Document
```java
@Autowired
private PostgresArticleDocumentRepository documentRepository;

// Save document to database
byte[] pdfContent = Files.readAllBytes(Paths.get("article.pdf"));
documentRepository.saveDocument(
    "ARTICLE-123",
    "research_paper.pdf",
    "application/pdf",
    pdfContent
);
```

### Sign Document
```java
@Autowired
private SignatureService signatureService;

@Autowired
private SecurityConfig securityConfig;

// Get current user from Spring Security
User currentUser = securityConfig.getCurrentUser();

// Sign the document
SignatureMetadata signature = signatureService.signDocument(
    "ARTICLE-123",
    currentUser,
    "I approve this article for publication"
);
```

### Verify Signatures
```java
boolean allValid = signatureService.verifySignatures("ARTICLE-123");
if (allValid) {
    documentRepository.updateStatus("ARTICLE-123", "APPROVED");
}
```

### REST API Endpoints

#### Sign Document
```bash
curl -X POST http://localhost:8080/api/signatures/sign/ARTICLE-123 \
  -H "Authorization: Bearer <token>" \
  -d '{"reason": "Approved for publication"}'
```

#### Verify All Signatures
```bash
curl http://localhost:8080/api/signatures/verify/ARTICLE-123 \
  -H "Authorization: Bearer <token>"
```

#### Get Signature List
```bash
curl http://localhost:8080/api/signatures/ARTICLE-123 \
  -H "Authorization: Bearer <token>"
```

#### Export User Certificate
```bash
curl http://localhost:8080/api/signatures/certificate/export \
  -H "Authorization: Bearer <token>"
```

---

## 🔄 Pipeline Integration

The e-signing feature integrates seamlessly with the existing pipeline:

```java
ApproveArticlePipeline pipeline = new ApproveArticlePipeline(
    signStep1,
    signStep2,
    signStep3,
    verifyStep
);

pipeline.execute(articleId);
```

### Workflow Steps
1. **RootStep** - Initialize with article ID
2. **SignDocumentStep** (×3) - Each reviewer signs
3. **VerifySignaturesStep** - Validate all signatures
4. **Status Update** - Mark as APPROVED

---

## 🔒 Security Considerations

### Production Recommendations

1. **Key Encryption at Rest**
   - Currently stores PEM keys as plain text
   - **TODO**: Implement encryption using AWS KMS, HashiCorp Vault, or database-level encryption

2. **Access Control**
   - Private keys only accessible by owner
   - Add authorization checks in `CertificateService.getUserKeyPair()`

3. **Certificate Revocation**
   - Implement CRL (Certificate Revocation List)
   - Add `revokedAt` field to `UserCertificateEntity`

4. **Audit Logging**
   - All signing operations logged with IP/user agent
   - Consider adding to centralized audit system

5. **Backup Strategy**
   - Regular database backups critical (keys stored in DB)
   - Consider HSM integration for high-security environments

---

## 🧪 Testing

### Unit Test Example
```java
@SpringBootTest
class SignatureServiceTest {
    
    @Autowired
    private SignatureService signatureService;
    
    @Autowired
    private PostgresArticleDocumentRepository docRepo;
    
    @Test
    void shouldSignAndVerifyDocument() {
        // Setup
        byte[] content = "Test document".getBytes();
        docRepo.saveDocument("TEST-1", "test.pdf", "application/pdf", content);
        
        User user = mock(User.class);
        when(user.name()).thenReturn("john.doe");
        
        // Sign
        SignatureMetadata sig = signatureService.signDocument("TEST-1", user, "Testing");
        
        // Verify
        boolean valid = signatureService.verifySignatures("TEST-1");
        assertTrue(valid);
    }
}
```

---

## 📊 Monitoring & Metrics

### Key Metrics to Track
- Number of signatures per article
- Certificate expiration dates
- Signing success/failure rates
- Document verification failures

### Database Queries
```sql
-- Count signatures per article
SELECT article_id, COUNT(*) 
FROM article_signatures 
GROUP BY article_id;

-- Find expiring certificates (next 30 days)
SELECT user_id, user_name, expires_at 
FROM user_certificates 
WHERE expires_at < NOW() + INTERVAL '30 days'
  AND is_active = TRUE;

-- Audit trail for specific article
SELECT * FROM article_signatures 
WHERE article_id = 'ARTICLE-123'
ORDER BY signed_at ASC;
```

---

## 🎯 Next Steps

1. **Replace Default Implementation** (already done!)
   - ✅ `PostgresArticleDocumentRepository` replaces placeholder
   
2. **Add Key Encryption** (recommended)
   - Integrate with cloud KMS or Vault
   
3. **Implement Certificate Renewal**
   - Scheduled job to renew expiring certificates
   
4. **Add UI Components**
   - Frontend signing interface
   - Signature visualization
   
5. **Email Notifications**
   - Notify reviewers when signature needed
   
6. **Performance Optimization**
   - Consider document compression for large files
   - Add caching layer for frequently accessed documents

---

## ✅ Integration Checklist

- [x] PostgreSQL database configured
- [x] Flyway migrations created (V1, V2)
- [x] JPA entities defined
- [x] Repository interfaces implemented
- [x] Service layer updated with persistent storage
- [x] PKI infrastructure with database backing
- [x] No entity duplication (reuses existing User)
- [x] Monolithic architecture (all in articles-pipeline)
- [x] Audit trail implemented
- [x] REST API endpoints available
- [x] Pipeline integration ready

---

## 📞 Support

For issues or questions:
1. Check database migration logs
2. Verify PostgreSQL connection settings
3. Ensure BouncyCastle provider is registered
4. Review application logs for certificate generation errors

**Migration Order**: V1 must run before V2 (table dependencies)
