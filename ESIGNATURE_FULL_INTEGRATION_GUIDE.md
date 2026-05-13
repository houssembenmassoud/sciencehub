# E-Signature Integration Guide for ScienceHub

## Overview
This guide provides a complete integration of e-signing with PKI (Public Key Infrastructure) and certificate generation into the ScienceHub application. The integration is **monolithic**, **reuses existing system data**, and **does not duplicate entities**.

## Key Design Principles

### ✅ What We Did
1. **No Entity Duplication**: Uses existing `User` interface from the pipeline module
2. **Monolithic Architecture**: All signing logic lives in `articles-pipeline` module
3. **Existing Auth Integration**: Retrieves user from Spring Security context
4. **PKI & Certificates**: Full cryptographic signing with BouncyCastle
5. **Pipeline-Native**: Integrates seamlessly with existing pipeline steps

### ❌ What We Avoided
- No separate `Signer` or `RequiredSigner` entities
- No duplication of user data
- No standalone authentication system
- No external dependencies beyond BouncyCastle (already included)

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   ScienceHub Application                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   Controller │    │   Security   │    │   Pipeline   │  │
│  │  (REST API)  │◄──►│    Config    │◄──►│    Steps     │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│         │                   │                   │           │
│         ▼                   ▼                   ▼           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              SignatureService                         │  │
│  │  - signDocument()                                     │  │
│  │  - verifySignatures()                                 │  │
│  │  - getSignatures()                                    │  │
│  └──────────────────────────────────────────────────────┘  │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │             CertificateService                        │  │
│  │  - generateKeyPair()                                  │  │
│  │  - issueCertificate()                                 │  │
│  │  - signData()                                         │  │
│  │  - verifySignature()                                  │  │
│  └──────────────────────────────────────────────────────┘  │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │          SignatureMetadata (JPA Entity)               │  │
│  │  - articleId (reference, no FK)                       │  │
│  │  - userId (from existing User.name())                 │  │
│  │  - documentHash (SHA-256)                             │  │
│  │  - signatureValue (Base64 encoded)                    │  │
│  │  - signedAt (timestamp)                               │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Files Created/Modified

### New Files

#### 1. Services
- `articles-pipeline/src/main/java/.../service/CertificateService.java`
  - Manages PKI infrastructure
  - Generates RSA key pairs per user
  - Issues X.509 certificates signed by internal CA
  - Provides sign/verify operations

- `articles-pipeline/src/main/java/.../service/SignatureService.java` (Modified)
  - Now integrates with CertificateService
  - Performs cryptographic signing
  - Verifies signatures using PKI

#### 2. Configuration
- `app/src/main/java/.../app/config/SecurityConfig.java`
  - Integrates with existing Spring Security
  - Provides `getCurrentUser()` method
  - No duplication of auth logic

#### 3. Controller
- `app/src/main/java/.../app/controller/SignatureController.java`
  - REST endpoints for signing operations
  - Uses SecurityConfig to get authenticated user
  - Endpoints:
    - `POST /api/signatures/sign/{articleId}` - Sign a document
    - `GET /api/signatures/verify/{articleId}` - Verify all signatures
    - `GET /api/signatures/{articleId}` - Get signature list
    - `GET /api/signatures/certificate/export` - Export user certificate
    - `POST /api/signatures/verify-single` - Verify single signature

#### 4. Database Migration
- `app/src/main/resources/db/migration/V2__add_signature_metadata.sql`
  - Creates `article_signatures` table
  - Stores metadata without duplicating User entity
  - Indexes for performance

### Existing Files (Unchanged)
- `SignatureMetadata.java` - JPA entity
- `SignatureMetadataRepository.java` - Spring Data repository
- `SignDocumentStep.java` - Pipeline step for signing
- `VerifySignaturesStep.java` - Pipeline step for verification
- `Document.java` / `DocumentImpl.java` - Document interface with signing
- `ApproveArticlePipeline.java` - Pipeline orchestration

## Usage

### 1. Pipeline-Based Signing (Recommended)

```java
// Create approval pipeline with 3 signers
ApproveArticlePipeline pipeline = ApproveArticlePipeline.createWithSigning(
    "ArticleApproval_v1",
    List.of("reviewer1", "reviewer2", "editor"),
    3  // Required signatures
);

// Register with runner and execute
PipelineRunner runner = new PipelineRunner();
runner.run(pipeline, null);
```

### 2. Direct API Usage

```bash
# Sign an article (requires authentication)
curl -X POST http://localhost:8080/api/signatures/sign/article-123 \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Approved for publication"}'

# Verify signatures
curl http://localhost:8080/api/signatures/verify/article-123

# Export certificate
curl http://localhost:8080/api/signatures/certificate/export \
  -H "Authorization: Bearer <token>"
```

### 3. Programmatic Usage

```java
@Autowired
private SignatureService signatureService;

@Autowired
private CertificateService certificateService;

// Get current user from security context
User currentUser = SecurityConfig.getCurrentUser();

// Sign document
byte[] documentContents = fetchDocument(articleId);
SignatureMetadata signature = signatureService.signDocument(
    articleId,
    currentUser,
    documentContents,
    "Final approval"
);

// Verify signatures
boolean isValid = signatureService.verifySignatures(articleId, documentContents);

// Export certificate
String certPem = certificateService.exportCertificate(
    currentUser.name(), 
    currentUser.name()
);
```

## Authentication Integration

The integration uses your **existing authentication system**:

```java
// In SecurityConfig.java
public static User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new IllegalStateException("No authenticated user found");
    }
    
    Object principal = authentication.getPrincipal();
    
    // Handles various principal types:
    // - Your custom User implementation
    // - String username
    // - UserDetails with getName() method
    // - Any object via reflection
    
    return createUserWrapper(principal);
}
```

**No changes needed to your existing auth!** The adapter pattern handles conversion.

## PKI Details

### Certificate Authority (CA)
- Self-signed CA created on application startup
- Valid for 10 years
- Used to sign all user certificates

### User Certificates
- RSA 2048-bit keys
- Valid for 1 year
- Automatically generated on first use
- Stored in-memory (can be extended to persistent storage)

### Signature Algorithm
- SHA256withRSA
- Compliant with most e-signature standards
- Non-repudiation support

## Database Schema

```sql
CREATE TABLE article_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id VARCHAR(255) NOT NULL,      -- Reference to article
    user_id VARCHAR(255) NOT NULL,         -- From User.name() (no FK)
    document_hash VARCHAR(64) NOT NULL,    -- SHA-256 hash
    signature_value TEXT,                  -- Base64 encoded signature
    signed_at TIMESTAMP NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_article_signatures_article_id ON article_signatures(article_id);
CREATE INDEX idx_article_signatures_user_id ON article_signatures(user_id);
CREATE UNIQUE INDEX idx_unique_article_user_signature 
    ON article_signatures(article_id, user_id);
```

## Security Considerations

1. **Key Storage**: Currently in-memory. For production:
   - Use HSM (Hardware Security Module)
   - Or encrypted database storage
   - Or cloud KMS (AWS KMS, Azure Key Vault)

2. **Certificate Revocation**: Implement CRL/OCSP for production

3. **Audit Trail**: All signatures include:
   - Timestamp
   - User ID
   - Document hash
   - Reason (optional)

4. **Document Integrity**: Hash verification ensures document wasn't modified after signing

## Testing

```java
@SpringBootTest
class SignatureIntegrationTest {
    
    @Autowired
    private SignatureService signatureService;
    
    @Autowired
    private CertificateService certificateService;
    
    @Test
    void testSigningAndVerification() throws Exception {
        // Create test user
        User testUser = () -> "test-user";
        
        // Document contents
        byte[] doc = "Test document".getBytes();
        
        // Sign
        SignatureMetadata sig = signatureService.signDocument(
            "test-article", 
            testUser, 
            doc, 
            "Test"
        );
        
        // Verify
        assertTrue(signatureService.verifySignatures("test-article", doc));
        
        // Tamper with document
        byte[] tampered = "Tampered document".getBytes();
        assertFalse(signatureService.verifySignatures("test-article", tampered));
    }
}
```

## Migration from Simple to PKI

If you previously used the simple (metadata-only) version:

1. **Existing signatures remain valid** - they just won't have cryptographic verification
2. **New signatures will include PKI** - full cryptographic verification
3. **Mixed mode supported** - `verifySignatures()` checks both hash and crypto signature

## Next Steps

1. **Configure your document storage**: Update `fetchDocumentContents()` in controller
2. **Customize CA settings**: Modify certificate validity periods in `CertificateService`
3. **Add certificate persistence**: Extend to store keys/certs in database or HSM
4. **Integrate with article workflow**: Add signing steps to your article approval pipelines
5. **Set up monitoring**: Track signature operations via Spring Boot Actuator

## Support

For issues or questions:
- Check pipeline execution logs
- Verify authentication is working (SecurityConfig.getCurrentUser())
- Ensure database migration ran successfully (V2__add_signature_metadata.sql)
- Confirm BouncyCastle provider is registered (check logs at startup)
