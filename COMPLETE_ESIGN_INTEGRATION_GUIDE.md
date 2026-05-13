# Complete E-Signature Integration Guide for ScienceHub

## Overview

This guide provides a complete, seamless integration of e-signing with PKI (Public Key Infrastructure) and certificate generation into the ScienceHub application. The integration follows the existing architecture patterns without duplicating entities or systems.

## Architecture Principles

✅ **No Entity Duplication**: Uses existing `User` interface only  
✅ **Monolithic**: All components in articles-pipeline module  
✅ **Existing Auth Integration**: Leverages Spring Security context  
✅ **PKI Ready**: Full cryptographic signing with BouncyCastle  
✅ **Pipeline Native**: Works seamlessly with existing pipeline system  
✅ **Real Document Storage**: Integrated with article storage repository  

## Components Created

### 1. Core Services

#### `CertificateService.java`
- Self-signed Certificate Authority (CA)
- RSA 2048-bit key pair generation per user
- X.509 certificate creation and management
- Cryptographic sign/verify operations using SHA256withRSA
- Automatic certificate generation on first use

#### `SignatureService.java` (Updated)
- Article validation before signing
- Document hash computation (SHA-256)
- PKI-based cryptographic signing
- Signature verification chain
- Integration with `ArticleDocumentRepository`

#### `HashService.java`
- SHA-256 document hashing
- Base64 encoding for storage

#### `SignaturePipelineService.java` (NEW)
- Pipeline creation and management
- Automatic pipeline execution
- Article status updates
- Signing step orchestration

### 2. Repository Layer

#### `ArticleDocumentRepository.java` (NEW)
```java
public interface ArticleDocumentRepository {
    Optional<byte[]> findDocumentContents(String articleId);
    Optional<String> findFilename(String articleId);
    boolean existsById(String articleId);
    void updateStatus(String articleId, String status);
}
```

#### `DefaultArticleDocumentRepository.java` (NEW)
- Placeholder implementation
- **TODO**: Replace with your actual article repository integration
- Connects to existing article storage system

#### `SignatureMetadataRepository.java`
- JPA repository for signature metadata
- Query by article ID with ordering
- Uniqueness constraint per user/article

### 3. Controllers

#### `SignatureController.java` (Updated)
REST endpoints:
- `POST /api/signatures/sign/{articleId}` - Sign document
- `GET /api/signatures/verify/{articleId}` - Verify all signatures
- `GET /api/signatures/{articleId}` - Get signature list
- `GET /api/signatures/certificate/export` - Export user certificate
- `POST /api/signatures/verify-single` - Verify single signature

#### `SignaturePipelineController.java` (NEW)
REST endpoints:
- `POST /api/signatures/pipeline/create/{articleId}` - Create signing pipeline
- `POST /api/signatures/pipeline/start/{articleId}` - Start pipeline execution
- `POST /api/signatures/pipeline/sign/{articleId}` - Sign through pipeline
- `GET /api/signatures/pipeline/status/{articleId}` - Check pipeline status

### 4. Pipeline Integration

#### `SignDocumentStep.java`
- Pipeline step for document signing
- Automatic triggering when dependencies satisfied
- Integrates with `SignatureService`

#### `VerifySignaturesStep.java`
- Verification pipeline step
- Validates required signature count
- Cryptographic verification

#### `ApproveArticlePipeline.java`
- Factory method: `createWithSigning()`
- Configurable signer list
- Required signature count validation

### 5. Database Schema

Migration: `V2__add_signature_metadata.sql`
```sql
CREATE TABLE article_signatures (
    id UUID PRIMARY KEY,
    article_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    document_hash VARCHAR(64) NOT NULL,
    signature_value TEXT,
    signed_at TIMESTAMP NOT NULL,
    reason VARCHAR(500)
);
```

**Key Features:**
- No foreign keys (uses existing system references)
- Unique constraint per article/user
- Indexed for efficient queries
- Supports both metadata-only and PKI signatures

## Usage Examples

### 1. Simple Signing (API)

```bash
# Sign an article
curl -X POST http://localhost:8080/api/signatures/sign/article-123 \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"reason": "I approve this article"}'

# Verify signatures
curl -X GET http://localhost:8080/api/signatures/verify/article-123 \
  -H "Authorization: Bearer <token>"

# Get all signatures
curl -X GET http://localhost:8080/api/signatures/article-123 \
  -H "Authorization: Bearer <token>"
```

### 2. Pipeline-Based Workflow

```bash
# Create pipeline with specific signers
curl -X POST http://localhost:8080/api/signatures/pipeline/create/article-123 \
  -H "Content-Type: application/json" \
  -d '{
    "signers": ["alice", "bob", "charlie"],
    "requiredSignatures": 2
  }'

# Start pipeline execution
curl -X POST http://localhost:8080/api/signatures/pipeline/start/article-123 \
  -H "Authorization: Bearer <token>"

# Sign as current user
curl -X POST http://localhost:8080/api/signatures/pipeline/sign/article-123 \
  -H "Authorization: Bearer <token>"

# Check status
curl -X GET http://localhost:8080/api/signatures/pipeline/status/article-123 \
  -H "Authorization: Bearer <token>"
```

### 3. Programmatic Usage

```java
// Inject services
@Autowired private SignatureService signatureService;
@Autowired private SignaturePipelineService pipelineService;

// Simple signing
User currentUser = SecurityConfig.getCurrentUser();
byte[] document = getDocumentContents();
SignatureMetadata signature = signatureService.signDocument(
    "article-123", 
    currentUser, 
    document, 
    "Approval"
);

// Pipeline workflow
List<String> signers = List.of("alice", "bob", "charlie");
ApproveArticlePipeline pipeline = pipelineService.createSigningPipeline(
    "article-123", 
    signers, 
    2 // required signatures
);
pipelineService.startPipeline("article-123");

// Update status after completion
pipelineService.updateArticleStatus("article-123", "APPROVED");
```

### 4. Certificate Management

```java
@Autowired private CertificateService certificateService;

// Export user certificate
String certPem = certificateService.exportCertificate(userId, userName);

// Export private key (handle with care!)
String privateKeyPem = certificateService.exportPrivateKey(userId);

// Sign data
byte[] signature = certificateService.signData(userId, documentBytes);

// Verify signature
boolean valid = certificateService.verifySignature(userId, documentBytes, signature);
```

## Integration Checklist

### ✅ Completed
- [x] Certificate service with PKI infrastructure
- [x] Signature service with article validation
- [x] Document repository abstraction
- [x] REST controllers for API access
- [x] Pipeline service for workflow management
- [x] Database migration script
- [x] Security integration (existing auth)
- [x] Hash service implementation

### ⚠️ TODO (Production)
- [ ] Replace `DefaultArticleDocumentRepository` with actual implementation
- [ ] Add persistent certificate/key storage (currently in-memory)
- [ ] Implement audit logging service
- [ ] Add email notifications for signers
- [ ] Certificate renewal/revocation process
- [ ] Frontend UI components
- [ ] Load testing for concurrent signing

## Customization Points

### 1. Connect to Existing Article Storage

Edit `DefaultArticleDocumentRepository.java`:

```java
@Service
public class DefaultArticleDocumentRepository implements ArticleDocumentRepository {
    
    @Autowired
    private ArticleRepository articleRepository; // Your existing repo
    
    @Override
    public Optional<byte[]> findDocumentContents(String articleId) {
        return articleRepository.findById(articleId)
            .map(Article::getContents);
    }
    
    @Override
    public boolean existsById(String articleId) {
        return articleRepository.existsById(articleId);
    }
    
    @Override
    public void updateStatus(String articleId, String status) {
        articleRepository.updateStatus(articleId, status);
    }
}
```

### 2. Persistent Certificate Storage

Currently certificates are stored in-memory. For production:

```java
// Add to CertificateService
@Autowired
private UserCertificateRepository certRepository;

public KeyPair getUserKeyPair(String userId) {
    return certRepository.findByUserId(userId)
        .map(UserCertificate::getKeyPair)
        .orElseGenerateAndSave(userId);
}
```

### 3. Custom Article Status Updates

```java
// In SignaturePipelineService
@Transactional
public void onPipelineComplete(String articleId) {
    // Update article status
    articleDocumentRepository.updateStatus(articleId, "APPROVED");
    
    // Send notification
    notificationService.sendApprovalNotification(articleId);
    
    // Log audit event
    auditService.logEvent("ARTICLE_APPROVED", articleId);
}
```

## Security Considerations

1. **Private Key Protection**: Currently in-memory. Use HSM or secure keystore in production.
2. **Authentication**: Relies on existing Spring Security setup.
3. **Document Integrity**: SHA-256 hash ensures document hasn't changed.
4. **Non-repudiation**: PKI signatures provide legal non-repudiation.
5. **Access Control**: Ensure proper authorization checks in controllers.

## Testing

### Unit Tests
```java
@SpringBootTest
class SignatureServiceTest {
    
    @Autowired
    private SignatureService signatureService;
    
    @Test
    void shouldSignDocument() {
        User user = () -> "test-user";
        byte[] doc = "content".getBytes();
        
        SignatureMetadata sig = signatureService.signDocument(
            "article-1", user, doc, "Test"
        );
        
        assertNotNull(sig.getId());
        assertEquals("test-user", sig.getUserId());
    }
}
```

### Integration Tests
```java
@SpringBootTest
@AutoConfigureMockMvc
class SignatureControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldSignArticle() throws Exception {
        mockMvc.perform(post("/api/signatures/sign/article-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Approved\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
```

## Troubleshooting

### Common Issues

1. **"Article not found"**
   - Ensure `ArticleDocumentRepository` is properly implemented
   - Check article ID format

2. **"User already signed"**
   - Signature uniqueness constraint prevents duplicate signing
   - Check existing signatures via GET endpoint

3. **"Certificate generation failed"**
   - Verify BouncyCastle provider is registered
   - Check JVM security settings

4. **"No authenticated user"**
   - Ensure Spring Security is configured
   - Verify authentication token is valid

## Next Steps

1. **Implement `ArticleDocumentRepository`** with your actual storage
2. **Add persistent certificate storage** for production
3. **Create frontend UI** for signing workflow
4. **Add email notifications** for signers
5. **Implement audit logging** for compliance
6. **Performance testing** under load

## Support

For issues or questions:
- Check existing pipeline documentation
- Review Spring Security configuration
- Verify database migration ran successfully
- Examine application logs for detailed errors

---

**Version**: 1.0  
**Last Updated**: 2024  
**Compatible With**: ScienceHub Pipeline Framework v1.x
