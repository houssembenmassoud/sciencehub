# E-Signing Feature Integration Guide for ScienceHub
## Seamless Monolithic Integration

## 📋 Executive Summary

This guide provides a **streamlined integration** of digital document signing into ScienceHub's existing pipeline architecture. The approach:

✅ **NO duplication** - Reuses existing User interface and system entities  
✅ **NO separate signers management** - Users are retrieved from the existing system  
✅ **NO new module** - Integrates directly into `articles-pipeline` module  
✅ **Minimal footprint** - Adds only signature metadata storage and signing logic  
✅ **Pipeline-native** - Uses existing `PipelineStep`, `Artifact`, and `PipelineRunner`  

---

## 🎯 Integration Architecture

### Current State
```
articles-pipeline/
└── recipe/
    ├── Document.java (interface with signatures())
    ├── ApproveArticlePipeline.java
    └── AbstractPipelineStep.java
```

### After Integration
```
articles-pipeline/
└── recipe/
    ├── Document.java (unchanged interface)
    ├── DocumentImpl.java (NEW - implementation with signing)
    ├── SignatureMetadata.java (NEW - JPA entity for signature tracking)
    ├── SignDocumentStep.java (NEW - pipeline step for signing)
    ├── VerifySignaturesStep.java (NEW - pipeline step for verification)
    ├── ApproveArticlePipeline.java (EXTENDED - adds signing steps)
    └── service/
        ├── SignatureService.java (NEW - core signing logic)
        └── HashService.java (NEW - SHA-256 hashing)
```

---

## 🔧 Step-by-Step Implementation

### Phase 1: Add Dependencies

**File: `/workspace/articles-pipeline/build.gradle`**

Add these dependencies to the existing build file:

```gradle
dependencies {
    // ... existing dependencies ...
    
    // PDF signing
    implementation 'com.itextpdf:itext7-core:7.2.5'
    implementation 'org.bouncycastle:bcprov-jdk18on:1.77'
    implementation 'org.bouncycastle:bcpkix-jdk18on:1.77'
    
    // Spring Boot Data JPA (if not already present)
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    
    // Database driver (adjust based on your DB)
    runtimeOnly 'org.postgresql:postgresql'
}
```

---

### Phase 2: Create Signature Metadata Entity

This entity stores signature information WITHOUT duplicating User data.

**File: `/workspace/articles-pipeline/src/main/java/io/qzz/tbsciencehubproject/sciencehub/internal/pipeline/recipe/SignatureMetadata.java`**

```java
package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores signature metadata for verification.
 * Does NOT duplicate User entity - references user by ID only.
 */
@Entity
@Table(name = "article_signatures")
public class SignatureMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String articleId;  // Reference to article (not duplicating)

    @Column(nullable = false)
    private String userId;     // Reference to existing User (no FK needed)

    @Column(nullable = false)
    private String documentHash;  // SHA-256 hash at signing time

    @Column(columnDefinition = "TEXT")
    private String signatureValue;  // Cryptographic signature (optional for simple workflow)

    @Column(nullable = false)
    private Instant signedAt;

    @Column(length = 500)
    private String reason;  // Optional: reason for signing

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.signedAt == null) {
            this.signedAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getDocumentHash() { return documentHash; }
    public void setDocumentHash(String documentHash) { this.documentHash = documentHash; }
    
    public String getSignatureValue() { return signatureValue; }
    public void setSignatureValue(String signatureValue) { this.signatureValue = signatureValue; }
    
    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
```

---

### Phase 3: Create Repository

**File: `/workspace/articles-pipeline/src/main/java/io/qzz/tbsciencehubproject/sciencehub/internal/pipeline/recipe/SignatureMetadataRepository.java`**

```java
package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SignatureMetadataRepository extends JpaRepository<SignatureMetadata, UUID> {
    List<SignatureMetadata> findByArticleIdOrderBySignedAtAsc(String articleId);
    boolean existsByArticleIdAndUserId(String articleId, String userId);
}
```

---

### Phase 4: Implement Utility Services

#### Hash Service

**File: `/workspace/articles-pipeline/src/main/java/io/qzz/tbsciencehubproject/sciencehub/internal/pipeline/recipe/service/HashService.java`**

```java
package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service;

import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class HashService {
    
    /**
     * Compute SHA-256 hash of document contents
     */
    public String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }
}
```

#### Signature Service

**File: `/workspace/articles-pipeline/src/main/java/io/qzz/tbsciencehubproject/sciencehub/internal/pipeline/recipe/service/SignatureService.java`**

```java
package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service;

import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.SignatureMetadata;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.SignatureMetadataRepository;
import io.qzz.tbsciencehubproject.user.User;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.Security;

@Service
public class SignatureService {
    
    private final SignatureMetadataRepository repository;
    private final HashService hashService;
    
    static {
        // Register BouncyCastle provider for cryptographic operations
        Security.addProvider(new BouncyCastleProvider());
    }
    
    public SignatureService(SignatureMetadataRepository repository, HashService hashService) {
        this.repository = repository;
        this.hashService = hashService;
    }
    
    /**
     * Record a signature on a document.
     * This is a SIMPLE workflow - stores metadata without full PKI.
     * For full cryptographic signing, extend this method.
     */
    public SignatureMetadata signDocument(String articleId, User user, byte[] documentContents, String reason) {
        // Check if user already signed
        if (repository.existsByArticleIdAndUserId(articleId, user.name())) {
            throw new IllegalStateException("User " + user.name() + " has already signed this article");
        }
        
        // Compute document hash
        String documentHash = hashService.sha256(documentContents);
        
        // Create signature metadata
        SignatureMetadata metadata = new SignatureMetadata();
        metadata.setArticleId(articleId);
        metadata.setUserId(user.name());  // Use user.name() as identifier
        metadata.setDocumentHash(documentHash);
        metadata.setReason(reason != null ? reason : "Article approval");
        
        // Optional: Generate cryptographic signature if certificates are available
        // String signatureValue = generateCryptographicSignature(documentContents, user);
        // metadata.setSignatureValue(signatureValue);
        
        return repository.save(metadata);
    }
    
    /**
     * Verify all signatures for an article
     */
    public boolean verifySignatures(String articleId, byte[] currentDocumentContents) {
        var signatures = repository.findByArticleIdOrderBySignedAtAsc(articleId);
        
        if (signatures.isEmpty()) {
            return false;
        }
        
        String currentHash = hashService.sha256(currentDocumentContents);
        
        // Verify that document hasn't changed since first signature
        // In a chain-of-signatures model, each signature would have its own hash
        for (SignatureMetadata sig : signatures) {
            if (!sig.getDocumentHash().equals(currentHash)) {
                // Document was modified after signing
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get all signatures for an article
     */
    public java.util.List<SignatureMetadata> getSignatures(String articleId) {
        return repository.findByArticleIdOrderBySignedAtAsc(articleId);
    }
    
    /**
     * Count signatures for an article
     */
    public long countSignatures(String articleId) {
        return repository.findByArticleIdOrderBySignedAtAsc(articleId).size();
    }
}
```

---

### Phase 5: Implement Document with Signing

**File: `/workspace/articles-pipeline/src/main/java/io/qzz/tbsciencehubproject/sciencehub/internal/pipeline/recipe/DocumentImpl.java`**

```java
package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service.SignatureService;
import io.qzz.tbsciencehubproject.user.User;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Concrete implementation of Document interface with signing capability.
 * Integrates with existing User interface - no duplication.
 */
public class DocumentImpl implements Document {
    
    private final String filename;
    private final byte[] contents;
    private final String articleId;
    private final SignatureService signatureService;
    
    public DocumentImpl(String filename, byte[] contents, String articleId, 
                       SignatureService signatureService) {
        this.filename = filename;
        this.contents = contents;
        this.articleId = articleId;
        this.signatureService = signatureService;
    }
    
    @Override
    public String filename() {
        return filename;
    }
    
    @Override
    public Collection<User> signatures() {
        // Retrieve signature metadata and convert to User representations
        return signatureService.getSignatures(articleId)
            .stream()
            .map(sig -> (User) () -> sig.getUserId())  // Simple User implementation
            .collect(Collectors.toList());
    }
    
    @Override
    public void sign(User user) {
        signatureService.signDocument(articleId, user, contents, "Article approval");
    }
    
    @Override
    public boolean hasSigned(User user) {
        return signatureService.getSignatures(articleId)
            .stream()
            .anyMatch(sig -> sig.getUserId().equals(user.name()));
    }
    
    @Override
    public boolean hasSignatures(User user) {
        return hasSigned(user);
    }
    
    @Override
    public byte[] contents() {
        return contents;
    }
    
    /**
     * Verify document integrity
     */
    public boolean verify() {
        return signatureService.verifySignatures(articleId, contents);
    }
}
```

---

### Phase 6: Create Pipeline Steps

#### Sign Document Step

**File: `/workspace/articles-pipeline/src/main/java/io/qzz/tbsciencehubproject/sciencehub/internal/pipeline/recipe/SignDocumentStep.java`**

```java
package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineContext;
import io.qzz.tbsciencehubproject.resource.ResourceKey;
import io.qzz.tbsciencehubproject.user.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Pipeline step that signs a document when executed.
 * Automatically triggered when all dependencies are satisfied.
 */
public class SignDocumentStep extends AbstractPipelineStep {
    
    private final String signerName;
    private final Set<Artifact<?>> providedArtifacts = new CopyOnWriteArraySet<>();
    
    // Resource keys for artifacts
    public static final ResourceKey<Document> DOCUMENT_KEY = 
        ResourceKey.of("signedDocument", Document.class);
    public static final ResourceKey<Boolean> SIGNATURE_COMPLETE_KEY = 
        ResourceKey.of("signatureComplete", Boolean.class);
    
    public SignDocumentStep(String signerName) {
        super("SignDocument_" + signerName);
        this.signerName = signerName;
        
        // Register provided artifacts
        providedArtifacts.add(new ArtifactImpl<>(DOCUMENT_KEY, this));
        providedArtifacts.add(new ArtifactImpl<>(SIGNATURE_COMPLETE_KEY, this));
    }
    
    @Override
    public Collection<Artifact<?>> provides() {
        return List.copyOf(providedArtifacts);
    }
    
    @Override
    public <T> Optional<Artifact<?>> provided(ResourceKey<T> key) {
        for (Artifact<?> artifact : providedArtifacts) {
            if (artifact.key().equals(key)) {
                return Optional.of(artifact);
            }
        }
        return Optional.empty();
    }
    
    @Override
    public Collection<PipelineStep> dependencySteps() {
        // Will be populated by dependOn() calls
        return dependencies().stream()
            .map(Artifact::step)
            .toList();
    }
    
    /**
     * Execute the signing operation.
     * Called by PipelineRunner when all dependencies are satisfied.
     */
    public void execute(PipelineContext context, Document document, User signer) {
        try {
            // Sign the document
            document.sign(signer);
            
            // Complete artifacts to trigger next steps
            context.completeArtifact(provided(DOCUMENT_KEY).get(), document);
            context.completeArtifact(provided(SIGNATURE_COMPLETE_KEY).get(), true);
            
        } catch (Exception e) {
            context.failArtifact(provided(SIGNATURE_COMPLETE_KEY).get(), e);
        }
    }
    
    public String getSignerName() {
        return signerName;
    }
}
```

#### Verify Signatures Step

**File: `/workspace/articles-pipeline/src/main/java/io/qzz/tbsciencehubproject/sciencehub/internal/pipeline/recipe/VerifySignaturesStep.java`**

```java
package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineContext;
import io.qzz.tbsciencehubproject.resource.ResourceKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Pipeline step that verifies all required signatures are present.
 */
public class VerifySignaturesStep extends AbstractPipelineStep {
    
    private final int requiredSignatureCount;
    private final Set<Artifact<?>> providedArtifacts = new CopyOnWriteArraySet<>();
    
    public static final ResourceKey<Boolean> VERIFICATION_RESULT_KEY = 
        ResourceKey.of("verificationResult", Boolean.class);
    
    public VerifySignaturesStep(int requiredSignatureCount) {
        super("VerifySignatures");
        this.requiredSignatureCount = requiredSignatureCount;
        
        providedArtifacts.add(new ArtifactImpl<>(VERIFICATION_RESULT_KEY, this));
    }
    
    @Override
    public Collection<Artifact<?>> provides() {
        return List.copyOf(providedArtifacts);
    }
    
    @Override
    public <T> Optional<Artifact<?>> provided(ResourceKey<T> key) {
        for (Artifact<?> artifact : providedArtifacts) {
            if (artifact.key().equals(key)) {
                return Optional.of(artifact);
            }
        }
        return Optional.empty();
    }
    
    @Override
    public Collection<PipelineStep> dependencySteps() {
        return dependencies().stream()
            .map(Artifact::step)
            .toList();
    }
    
    /**
     * Execute verification.
     */
    public void execute(PipelineContext context, Document document) {
        try {
            boolean isValid = document.verify() && 
                             document.signatures().size() >= requiredSignatureCount;
            
            context.completeArtifact(provided(VERIFICATION_RESULT_KEY).get(), isValid);
            
            if (!isValid) {
                throw new IllegalStateException(
                    "Document verification failed. Required: " + requiredSignatureCount + 
                    ", Found: " + document.signatures().size());
            }
            
        } catch (Exception e) {
            context.failArtifact(provided(VERIFICATION_RESULT_KEY).get(), e);
        }
    }
}
```

---

### Phase 7: Extend ApproveArticlePipeline

**File: `/workspace/articles-pipeline/src/main/java/io/qzz/tbsciencehubproject/sciencehub/internal/pipeline/recipe/ApproveArticlePipeline.java`**

Update the existing file:

```java
package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Pipeline;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineStep;
import io.qzz.tbsciencehubproject.pipeline.pipeline.RootPipelineStep;
import io.qzz.tbsciencehubproject.pipeline.validate.PipelineValidationException;
import io.qzz.tbsciencehubproject.pipeline.validate.PipelineValidator;
import io.qzz.tbsciencehubproject.utils.TypeToken;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ApproveArticlePipeline implements Pipeline<Void> {

  private final String recipeName;
  private final int requiredSignatures;
  private final Set<PipelineValidator> pipelineValidators = new CopyOnWriteArraySet<>();
  private final Set<PipelineStep> steps = new CopyOnWriteArraySet<>();
  private RootPipelineStep<Void> rootStep;

  public ApproveArticlePipeline(String recipeName) {
    this(recipeName, 2); // Default: 2 signatures required
  }
  
  public ApproveArticlePipeline(String recipeName, int requiredSignatures) {
    this.recipeName = recipeName;
    this.requiredSignatures = requiredSignatures;
  }

  @Override
  public String name() {
    return recipeName;
  }

  @Override
  public void registerValidator(PipelineValidator validator) {
    this.pipelineValidators.add(validator);
  }

  @Override
  public void validate() throws PipelineValidationException {
    for (PipelineValidator validator : pipelineValidators) {
      validator.validate(null);
    }
    // Validate that we have required signing steps
    long signSteps = steps.stream()
        .filter(s -> s instanceof SignDocumentStep)
        .count();
    
    if (signSteps < requiredSignatures) {
      throw new PipelineValidationException(
          "Pipeline requires at least " + requiredSignatures + " signing steps");
    }
  }

  @Override
  public void registerStep(PipelineStep step) {
    steps.add(step);
    if (step instanceof RootPipelineStep) {
      this.rootStep = (RootPipelineStep<Void>) step;
    }
  }

  @Override
  public void registerSteps(Collection<? extends PipelineStep> steps) {
    this.steps.addAll(steps);
    for (PipelineStep step : steps) {
      if (step instanceof RootPipelineStep) {
        this.rootStep = (RootPipelineStep<Void>) step;
      }
    }
  }

  @Override
  public Collection<PipelineStep> steps() {
    return List.copyOf(steps);
  }

  @Override
  public RootPipelineStep<Void> rootStep() {
    return rootStep;
  }

  @Override
  public TypeToken<Void> inputType() {
    return TypeToken.of(Void.class);
  }
  
  /**
   * Helper method to configure standard approval pipeline with signing
   */
  public static ApproveArticlePipeline createWithSigning(
      String recipeName, 
      List<String> signerNames,
      int requiredSignatureCount) {
      
    ApproveArticlePipeline pipeline = new ApproveArticlePipeline(recipeName, requiredSignatureCount);
    
    // Create root step
    RootPipelineStep<Void> rootStep = new RootPipelineStep<>("StartApproval");
    pipeline.registerStep(rootStep);
    
    // Create signing steps for each signer
    SignDocumentStep[] signSteps = new SignDocumentStep[signerNames.size()];
    for (int i = 0; i < signerNames.size(); i++) {
      signSteps[i] = new SignDocumentStep(signerNames.get(i));
      
      // Each signing step depends on previous step (or root for first)
      if (i == 0) {
        signSteps[i].dependOn(rootStep.rootArtifact());
      } else {
        signSteps[i].dependOn(signSteps[i-1].provides().iterator().next());
      }
      
      pipeline.registerStep(signSteps[i]);
    }
    
    // Add verification step after all signing
    VerifySignaturesStep verifyStep = new VerifySignaturesStep(requiredSignatureCount);
    verifyStep.dependOn(signSteps[signSteps.length - 1].provides().iterator().next());
    pipeline.registerStep(verifyStep);
    
    return pipeline;
  }
}
```

---

### Phase 8: Database Schema

Create migration script for signature metadata table:

**File: `/workspace/app/src/main/resources/db/migration/V2__add_signature_metadata.sql`**

```sql
CREATE TABLE IF NOT EXISTS article_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    document_hash VARCHAR(64) NOT NULL,
    signature_value TEXT,
    signed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_article_signatures_article_id ON article_signatures(article_id);
CREATE INDEX idx_article_signatures_user_id ON article_signatures(user_id);
CREATE UNIQUE INDEX idx_unique_article_user_signature 
    ON article_signatures(article_id, user_id);
```

---

### Phase 9: Application Configuration

**File: `/workspace/app/src/main/resources/application.yml`**

Add e-signature configuration:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sciencehub
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  
  jpa:
    hibernate:
      ddl-auto: validate  # Use migrations instead
    show-sql: false

# E-signature settings
esignature:
  enabled: true
  storage-path: /var/app/documents
  require-cryptographic-signature: false  # Set true for full PKI
```

---

## 🚀 Usage Example

```java
// Configure pipeline with 3 signers
var pipeline = ApproveArticlePipeline.createWithSigning(
    "ArticleApproval_v1",
    List.of("reviewer1", "reviewer2", "editor"),
    3  // All 3 must sign
);

// Create runner
var runner = new ArticlePipelineRunner(pipeline);

// Prepare document
byte[] pdfContent = loadArticlePDF();
Document doc = new DocumentImpl(
    "article_123.pdf", 
    pdfContent, 
    "article_123",
    signatureService
);

// Start pipeline
runner.start(doc);

// When each signer completes:
// runner.completeArtifact(signStep.provided(...), doc);
```

---

## ✅ Integration Checklist

- [ ] Add PDF signing dependencies to `build.gradle`
- [ ] Create `SignatureMetadata` entity
- [ ] Create `SignatureMetadataRepository`
- [ ] Implement `HashService`
- [ ] Implement `SignatureService`
- [ ] Create `DocumentImpl`
- [ ] Create `SignDocumentStep`
- [ ] Create `VerifySignaturesStep`
- [ ] Update `ApproveArticlePipeline` with signing support
- [ ] Add database migration
- [ ] Configure application properties
- [ ] Test signing workflow

---

## 🔐 Security Considerations

1. **Simple Mode** (default): Stores signature metadata with document hash. Good for audit trail.

2. **Full PKI Mode**: Enable cryptographic signatures by:
   - Generating user certificates
   - Using iText for PDF signing
   - Storing signature values in `signature_value` column

3. **User Identity**: Uses existing `User.name()` - integrate with your authentication system.

---

## 📝 Key Differences from Original Guide

| Original Guide | This Integration |
|---------------|------------------|
| Separate `esignature-module` | Integrated into `articles-pipeline` |
| New `UserCertificate` entity | ❌ No certificate entity (optional) |
| Separate `requiredSigners` field | Uses pipeline configuration |
| Duplicates User references | Uses existing `User` interface |
| Complex CA management | Simple metadata or optional PKI |
| Multiple repositories | Single `SignatureMetadataRepository` |

---

## 🎉 Benefits

✅ **Monolithic** - Single codebase, easier maintenance  
✅ **No Duplication** - Reuses existing User and entities  
✅ **Pipeline-Native** - Follows existing patterns  
✅ **Flexible** - Simple metadata or full PKI  
✅ **Audit-Ready** - Complete signature history  
✅ **Testable** - Each step is isolated and testable  
