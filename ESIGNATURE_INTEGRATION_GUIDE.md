# E-Signing Feature Integration Guide for ScienceHub

## 📋 Executive Summary

This guide explains how to integrate the **Digital Document Signature System** into the existing ScienceHub application using its pipeline architecture. The integration will enable multi-signature workflows for article approval with PKI verification.

---

## 🎯 Integration Strategy

The e-signing feature will be integrated as a **new pipeline module** that:
1. Extends the existing `ApproveArticlePipeline` with signature steps
2. Adds Spring Boot REST controllers for certificate management and signing operations
3. Implements JPA entities for document, signature, and certificate storage
4. Leverages the existing pipeline runner for workflow orchestration

---

## 📁 Proposed Module Structure

```
/workspace
├── app/                          # Spring Boot application (existing)
├── pipeline/                     # Core pipeline framework (existing)
├── articles-pipeline/            # Article workflows (existing)
└── esignature-module/            # NEW: E-signature module
    ├── build.gradle
    └── src/main/java/io/qzz/tbsciencehubproject/esignature/
        ├── config/
        │   ├── SecurityConfig.java           # BouncyCastle provider
        │   └── ESignatureConfig.java         # Configuration properties
        ├── controller/
        │   └── DocumentController.java       # REST API endpoints
        ├── service/
        │   ├── SignatureService.java         # Core signing logic
        │   ├── DigitalSignatureService.java  # PDF cryptographic signing
        │   ├── PKIVerificationService.java   # Signature verification
        │   ├── CertificateGenerationService.java  # User certificates
        │   ├── InternalCAService.java        # CA keystore management
        │   ├── StorageService.java           # File storage
        │   └── HashService.java              # SHA-256 hashing
        ├── repository/
        │   ├── DocumentRepository.java
        │   ├── SignatureRepository.java
        │   ├── UserCertificateRepository.java
        │   └── AuditLogRepository.java
        ├── model/
        │   ├── Document.java                 # JPA entity
        │   ├── Signature.java                # JPA entity
        │   ├── UserCertificate.java          # JPA entity
        │   ├── AuditLog.java                 # JPA entity
        │   └── DocumentStatus.java           # Enum
        ├── pipeline/
        │   ├── GenerateCertificateStep.java  # Pipeline step
        │   ├── SignDocumentStep.java         # Pipeline step
        │   ├── VerifySignaturesStep.java     # Pipeline step
        │   └── ESignaturePipeline.java       # Pipeline implementation
        └── util/
            └── AesGcmUtils.java              # Encryption utility
```

---

## 🔧 Step-by-Step Integration

### Phase 1: Create the E-Signature Module

#### 1.1 Create Module Directory and Build File

**File: `/workspace/esignature-module/build.gradle`**
```gradle
plugins {
    id 'java-common-conventions'
}

dependencies {
    implementation(project(":pipeline"))
    implementation(project(":articles-pipeline"))
    
    // Spring Boot starters
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    
    // PDF signing
    implementation 'com.itextpdf:itext7-core:7.2.5'
    implementation 'org.bouncycastle:bcprov-jdk18on:1.77'
    implementation 'org.bouncycastle:bcpkix-jdk18on:1.77'
    
    // Database
    runtimeOnly 'org.postgresql:postgresql'
    
    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

#### 1.2 Update settings.gradle

**File: `/workspace/settings.gradle`**
```gradle
rootProject.name = 'sciencehub'

include 'app'
include 'pipeline'
include 'articles-pipeline'
include 'esignature-module'  // ADD THIS LINE
```

---

### Phase 2: Implement Core Services

#### 2.1 Security Configuration

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/config/SecurityConfig.java`**
```java
package io.qzz.tbsciencehubproject.esignature.config;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Configuration;
import java.security.Security;

@Configuration
public class SecurityConfig {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
}
```

#### 2.2 Configuration Properties

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/config/ESignatureConfig.java`**
```java
package io.qzz.tbsciencehubproject.esignature.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "esignature")
public class ESignatureConfig {
    private String storagePath = "/var/app/documents";
    private String caPath = "/var/app/ca/internal-ca.p12";
    private String caPassword = "changeit-ca";
    private String masterKey;
    private int defaultRequiredSigners = 3;

    // Getters and setters
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getCaPath() { return caPath; }
    public void setCaPath(String caPath) { this.caPath = caPath; }
    public String getCaPassword() { return caPassword; }
    public void setCaPassword(String caPassword) { this.caPassword = caPassword; }
    public String getMasterKey() { return masterKey; }
    public void setMasterKey(String masterKey) { this.masterKey = masterKey; }
    public int getDefaultRequiredSigners() { return defaultRequiredSigners; }
    public void setDefaultRequiredSigners(int defaultRequiredSigners) { this.defaultRequiredSigners = defaultRequiredSigners; }
}
```

#### 2.3 Data Models (JPA Entities)

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/model/DocumentStatus.java`**
```java
package io.qzz.tbsciencehubproject.esignature.model;

public enum DocumentStatus {
    DRAFT, 
    PENDING_SIGNATURE, 
    FULLY_SIGNED, 
    ARCHIVED
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/model/Document.java`**
```java
package io.qzz.tbsciencehubproject.esignature.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Data
@Table(name = "esign_documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String filePath;
    private String currentHash;

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    private int requiredSigners;
    private int signedCount;
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/model/Signature.java`**
```java
package io.qzz.tbsciencehubproject.esignature.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "esign_signatures")
public class Signature {
    @Id
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    private UUID documentId;
    private String userId;
    private String previousHash;
    private String signatureHash;
    private Instant signedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.signedAt == null) {
            this.signedAt = Instant.now();
        }
    }
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/model/UserCertificate.java`**
```java
package io.qzz.tbsciencehubproject.esignature.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Data
@Table(name = "esign_user_certificates")
public class UserCertificate {
    @Id
    private String userId;

    @Column(columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    @Column(columnDefinition = "TEXT")
    private String certificatePem;

    private Instant createdAt;
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/model/AuditLog.java`**
```java
package io.qzz.tbsciencehubproject.esignature.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "esign_audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID documentId;
    private String actorId;
    private String action;

    @Column(columnDefinition = "TEXT")
    private String details;

    private String ipAddress;
    private Instant timestamp;
}
```

#### 2.4 Repositories

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/repository/DocumentRepository.java`**
```java
package io.qzz.tbsciencehubproject.esignature.repository;

import io.qzz.tbsciencehubproject.esignature.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/repository/SignatureRepository.java`**
```java
package io.qzz.tbsciencehubproject.esignature.repository;

import io.qzz.tbsciencehubproject.esignature.model.Signature;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SignatureRepository extends JpaRepository<Signature, Long> {
    List<Signature> findByDocumentIdOrderBySignedAtAsc(UUID documentId);
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/repository/UserCertificateRepository.java`**
```java
package io.qzz.tbsciencehubproject.esignature.repository;

import io.qzz.tbsciencehubproject.esignature.model.UserCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCertificateRepository extends JpaRepository<UserCertificate, String> {
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/repository/AuditLogRepository.java`**
```java
package io.qzz.tbsciencehubproject.esignature.repository;

import io.qzz.tbsciencehubproject.esignature.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByDocumentIdOrderByTimestampDesc(UUID documentId);
}
```

#### 2.5 Utility Services

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/util/AesGcmUtils.java`**
```java
package io.qzz.tbsciencehubproject.esignature.util;

import io.qzz.tbsciencehubproject.esignature.config.ESignatureConfig;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class AesGcmUtils {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;

    private final SecretKeySpec keySpec;

    public AesGcmUtils(ESignatureConfig config) {
        String masterKeyHex = config.getMasterKey();
        if (masterKeyHex == null || masterKeyHex.length() != 64) {
            throw new IllegalArgumentException("Master key must be a 64-character hex string");
        }
        byte[] key = HexFormat.of().parseHex(masterKeyHex);
        this.keySpec = new SecretKeySpec(key, "AES");
    }

    public String encrypt(byte[] plaintext) throws Exception {
        byte[] iv = new byte[IV_LEN];
        SecureRandom.getInstanceStrong().nextBytes(iv);
        Cipher c = Cipher.getInstance(ALGORITHM);
        c.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN, iv));
        byte[] ct = c.doFinal(plaintext);
        byte[] out = new byte[IV_LEN + ct.length];
        System.arraycopy(iv, 0, out, 0, IV_LEN);
        System.arraycopy(ct, 0, out, IV_LEN, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    public byte[] decrypt(String b64) throws Exception {
        byte[] raw = Base64.getDecoder().decode(b64);
        byte[] iv = Arrays.copyOfRange(raw, 0, IV_LEN);
        byte[] ct = Arrays.copyOfRange(raw, IV_LEN, raw.length);
        Cipher c = Cipher.getInstance(ALGORITHM);
        c.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN, iv));
        return c.doFinal(ct);
    }
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/service/HashService.java`**
```java
package io.qzz.tbsciencehubproject.esignature.service;

import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class HashService {
    public String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/service/StorageService.java`**
```java
package io.qzz.tbsciencehubproject.esignature.service;

import io.qzz.tbsciencehubproject.esignature.config.ESignatureConfig;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class StorageService {
    private final String storagePath;

    public StorageService(ESignatureConfig config) {
        this.storagePath = config.getStoragePath();
    }

    public String save(byte[] data, String name) throws IOException {
        File dir = new File(storagePath);
        dir.mkdirs();
        String path = storagePath + "/" + name;
        Files.write(Paths.get(path), data);
        return path;
    }

    public byte[] load(String path) throws IOException {
        return Files.readAllBytes(Paths.get(path));
    }
}
```

#### 2.6 Core Services

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/service/InternalCAService.java`**
```java
package io.qzz.tbsciencehubproject.esignature.service;

import io.qzz.tbsciencehubproject.esignature.config.ESignatureConfig;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

@Component
public class InternalCAService {
    private static final String CA_ALIAS = "university-internal-ca";
    private final String caPath;
    private final String caPassword;

    public InternalCAService(ESignatureConfig config) {
        this.caPath = config.getCaPath();
        this.caPassword = config.getCaPassword();
    }

    public KeyStore getCAKeyStore() throws Exception {
        File f = new File(caPath);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        char[] pwd = caPassword.toCharArray();
        if (!f.exists()) {
            f.getParentFile().mkdirs();
            generateCA(ks, pwd);
        } else {
            ks.load(new FileInputStream(f), pwd);
        }
        return ks;
    }

    private void generateCA(KeyStore ks, char[] pwd) throws Exception {
        ks.load(null, null);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X500Name name = new X500Name("CN=University Internal CA,O=ScienceHub,C=US");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date from = new Date();
        Date to = new Date(System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(kp.getPrivate());
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, serial, from, to, name, kp.getPublic());

        X509CertificateHolder h = builder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(h);

        ks.setKeyEntry(CA_ALIAS, kp.getPrivate(), pwd, new Certificate[]{cert});
        ks.store(new FileOutputStream(caPath), pwd);
    }
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/service/CertificateGenerationService.java`**
```java
package io.qzz.tbsciencehubproject.esignature.service;

import io.qzz.tbsciencehubproject.esignature.model.UserCertificate;
import io.qzz.tbsciencehubproject.esignature.repository.UserCertificateRepository;
import io.qzz.tbsciencehubproject.esignature.util.AesGcmUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class CertificateGenerationService {
    private final InternalCAService caService;
    private final UserCertificateRepository userCertRepo;
    private final AesGcmUtils aesGcmUtils;

    public CertificateGenerationService(InternalCAService caService,
                                       UserCertificateRepository userCertRepo,
                                       AesGcmUtils aesGcmUtils) {
        this.caService = caService;
        this.userCertRepo = userCertRepo;
        this.aesGcmUtils = aesGcmUtils;
    }

    public Map<String, Object> generateCertificate(String userId, String role) throws Exception {
        if (userCertRepo.existsById(userId)) {
            throw new RuntimeException("Certificate already exists for: " + userId);
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair userKeys = kpg.generateKeyPair();

        KeyStore caKs = caService.getCAKeyStore();
        PrivateKey caKey = (PrivateKey) caKs.getKey("university-internal-ca",
                caService.getClass().getDeclaredField("caPassword").get(caService).toString().toCharArray());
        X509Certificate caCert = (X509Certificate) caKs.getCertificate("university-internal-ca");

        X500Name subject = new X500Name(
                "CN=" + userId + ",OU=" + role + ",O=ScienceHub,C=US");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date from = new Date();
        Date to = new Date(System.currentTimeMillis() + 2L * 365 * 24 * 60 * 60 * 1000);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name(caCert.getSubjectX500Principal().getName()),
                serial, from, to, subject, userKeys.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(caKey);
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate userCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);

        String encryptedKey = aesGcmUtils.encrypt(userKeys.getPrivate().getEncoded());

        StringWriter sw = new StringWriter();
        try (JcaPEMWriter pw = new JcaPEMWriter(sw)) {
            pw.writeObject(userCert);
        }
        String pem = sw.toString();

        UserCertificate uc = new UserCertificate();
        uc.setUserId(userId);
        uc.setEncryptedPrivateKey(encryptedKey);
        uc.setCertificatePem(pem);
        uc.setCreatedAt(Instant.now());
        userCertRepo.save(uc);

        Map<String, Object> res = new HashMap<>();
        res.put("userId", userId);
        res.put("certificatePem", pem);
        res.put("thumbprint", sha256Thumbprint(pem));
        res.put("status", "registered");
        res.put("expiresAt", to.toInstant().toString());
        return res;
    }

    private String sha256Thumbprint(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(input.getBytes()));
    }
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/service/DigitalSignatureService.java`**
```java
package io.qzz.tbsciencehubproject.esignature.service;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.signatures.*;
import io.qzz.tbsciencehubproject.esignature.model.UserCertificate;
import io.qzz.tbsciencehubproject.esignature.repository.UserCertificateRepository;
import io.qzz.tbsciencehubproject.esignature.util.AesGcmUtils;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

@Service
public class DigitalSignatureService {
    private final UserCertificateRepository userCertRepo;
    private final AesGcmUtils aesGcmUtils;
    private final InternalCAService caService;

    public DigitalSignatureService(UserCertificateRepository userCertRepo,
                                   AesGcmUtils aesGcmUtils,
                                   InternalCAService caService) {
        this.userCertRepo = userCertRepo;
        this.aesGcmUtils = aesGcmUtils;
        this.caService = caService;
    }

    public byte[] signPDF(byte[] pdfBytes, String userId, int signatureIndex) throws Exception {
        UserCertificate uc = userCertRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("No certificate for user: " + userId));

        byte[] pkBytes = aesGcmUtils.decrypt(uc.getEncryptedPrivateKey());
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pkBytes));

        X509Certificate userCert = loadCert(uc.getCertificatePem());
        X509Certificate caCert = (X509Certificate) caService.getCAKeyStore()
                .getCertificate("university-internal-ca");
        Certificate[] chain = new Certificate[]{userCert, caCert};

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
        PdfSigner signer = new PdfSigner(reader, baos, new StampingProperties().useAppendMode());

        int x = 36;
        int y = 36 + signatureIndex * 90;
        Rectangle rect = new Rectangle(x, y, 200, 80);

        PdfSignatureAppearance appearance = signer.getSignatureAppearance();
        appearance.setReason("University Document Signing")
                .setLocation("ScienceHub")
                .setRenderingMode(PdfSignatureAppearance.RenderingMode.DESCRIPTION)
                .setPageRect(rect)
                .setPageNumber(1);
        
        String fieldName = "sig_" + userId.toString().replace(".", "_").replace("-", "_");
        signer.setFieldName(fieldName + "_" + System.currentTimeMillis());

        IExternalSignature pks = new PrivateKeySignature(privateKey, DigestAlgorithms.SHA256, "BC");
        IExternalDigest digest = new BouncyCastleDigest();

        signer.signDetached(digest, pks, chain, null, null, null, 0, PdfSigner.CryptoStandard.CMS);
        return baos.toByteArray();
    }

    private X509Certificate loadCert(String pem) throws Exception {
        try (PEMParser p = new PEMParser(new StringReader(pem))) {
            X509CertificateHolder h = (X509CertificateHolder) p.readObject();
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(h);
        }
    }
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/service/PKIVerificationService.java`**
```java
package io.qzz.tbsciencehubproject.esignature.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.signatures.PdfPKCS7;
import com.itextpdf.signatures.SignatureUtil;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PKIVerificationService {
    private final InternalCAService caService;

    public PKIVerificationService(InternalCAService caService) {
        this.caService = caService;
    }

    public Map<String, Object> verifyDocument(byte[] pdfBytes) throws Exception {
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)));
        SignatureUtil util = new SignatureUtil(pdfDoc);
        List<String> names = util.getSignatureNames();

        X509Certificate caCert = (X509Certificate) caService.getCAKeyStore()
                .getCertificate("university-internal-ca");

        List<Map<String, Object>> sigs = new ArrayList<>();
        boolean allValid = true;

        for (String name : names) {
            PdfPKCS7 pkcs7 = util.readSignatureData(name);
            boolean sigIntact = pkcs7.verifySignatureIntegrityAndAuthenticity();
            boolean docNotModified = util.signatureCoversWholeDocument(name);
            String signerName = extractSignerName(pkcs7);
            String signDateStr = formatSignDate(pkcs7.getSignDate());

            boolean chainValid = false;
            X509Certificate signCert = pkcs7.getSigningCertificate();
            if (signCert != null) {
                try {
                    signCert.verify(caCert.getPublicKey());
                    chainValid = true;
                } catch (Exception ignored) {}
            }

            boolean valid = sigIntact && docNotModified && chainValid;
            if (!valid) allValid = false;

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("fieldName", name);
            info.put("signer", signerName);
            info.put("signatureValid", sigIntact);
            info.put("documentNotModified", docNotModified);
            info.put("chainValid", chainValid);
            info.put("valid", valid);
            info.put("signDate", signDateStr);

            sigs.add(info);
        }

        pdfDoc.close();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allValid", allValid);
        result.put("signatureCount", names.size());
        result.put("signatures", sigs);
        return result;
    }

    private String extractSignerName(PdfPKCS7 pkcs7) {
        String signerName = pkcs7.getSignName();
        if (signerName != null && !signerName.isBlank()) {
            return signerName.trim();
        }

        X509Certificate cert = pkcs7.getSigningCertificate();
        if (cert != null) {
            String subject = cert.getSubjectX500Principal().getName();
            String cn = extractCommonName(subject);
            if (cn != null && !cn.isBlank()) {
                return cn;
            }
        }

        return "Unknown Signer";
    }

    private String extractCommonName(String distinguishedName) {
        if (distinguishedName == null) return null;
        Matcher matcher = Pattern.compile("(?i)CN\\s*=\\s*([^,]+)").matcher(distinguishedName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        matcher = Pattern.compile("(?i)CN\\s*=\\s*([^,]*(?:\\\\,[^,]*)*)").matcher(distinguishedName);
        if (matcher.find()) {
            return matcher.group(1).replace("\\,", ",").trim();
        }
        return distinguishedName;
    }

    private String formatSignDate(Calendar calendar) {
        if (calendar == null) return null;
        try {
            long timeInMillis = calendar.getTimeInMillis();
            if (timeInMillis <= 0) return null;
            return calendar.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/service/SignatureService.java`**
```java
package io.qzz.tbsciencehubproject.esignature.service;

import io.qzz.tbsciencehubproject.esignature.config.ESignatureConfig;
import io.qzz.tbsciencehubproject.esignature.model.Document;
import io.qzz.tbsciencehubproject.esignature.model.DocumentStatus;
import io.qzz.tbsciencehubproject.esignature.model.Signature;
import io.qzz.tbsciencehubproject.esignature.repository.AuditLogRepository;
import io.qzz.tbsciencehubproject.esignature.repository.DocumentRepository;
import io.qzz.tbsciencehubproject.esignature.repository.SignatureRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SignatureService {
    private final DocumentRepository documentRepo;
    private final SignatureRepository signatureRepo;
    private final StorageService storage;
    private final HashService hashService;
    private final DigitalSignatureService digitalSignatureService;
    private final PKIVerificationService pkiVerificationService;
    private final AuditLogRepository auditLogRepo;
    private final int defaultRequiredSigners;

    public SignatureService(DocumentRepository documentRepo,
                           SignatureRepository signatureRepo,
                           StorageService storage,
                           HashService hashService,
                           DigitalSignatureService digitalSignatureService,
                           PKIVerificationService pkiVerificationService,
                           AuditLogRepository auditLogRepo,
                           ESignatureConfig config) {
        this.documentRepo = documentRepo;
        this.signatureRepo = signatureRepo;
        this.storage = storage;
        this.hashService = hashService;
        this.digitalSignatureService = digitalSignatureService;
        this.pkiVerificationService = pkiVerificationService;
        this.auditLogRepo = auditLogRepo;
        this.defaultRequiredSigners = config.getDefaultRequiredSigners();
    }

    public Document signNew(byte[] file, String userId) throws Exception {
        byte[] signed = digitalSignatureService.signPDF(file, userId, 0);
        String path = storage.save(signed, UUID.randomUUID() + ".pdf");
        String hash = hashService.sha256(signed);

        Document doc = new Document();
        doc.setFilePath(path);
        doc.setCurrentHash(hash);
        doc.setStatus(DocumentStatus.PENDING_SIGNATURE);
        doc.setRequiredSigners(defaultRequiredSigners);
        doc.setSignedCount(1);
        documentRepo.save(doc);

        saveSignatureRecord(doc.getId(), userId, null, hash);
        logAudit(doc.getId(), userId, "DOCUMENT_CREATED_AND_SIGNED",
                "First signature by " + userId);
        return doc;
    }

    public void addSignature(UUID docId, String userId) throws Exception {
        Document doc = documentRepo.findById(docId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (doc.getStatus() != DocumentStatus.PENDING_SIGNATURE) {
            throw new RuntimeException("Document not open for signing. Status: " + doc.getStatus());
        }

        boolean alreadySigned = signatureRepo.findByDocumentIdOrderBySignedAtAsc(docId)
                .stream().anyMatch(s -> s.getUserId().equals(userId));
        if (alreadySigned) {
            throw new RuntimeException("User already signed this document");
        }

        byte[] current = storage.load(doc.getFilePath());
        byte[] signed = digitalSignatureService.signPDF(current, userId, doc.getSignedCount());
        String newPath = storage.save(signed, UUID.randomUUID() + ".pdf");
        String newHash = hashService.sha256(signed);
        String prevHash = doc.getCurrentHash();

        saveSignatureRecord(docId, userId, prevHash, newHash);

        doc.setFilePath(newPath);
        doc.setCurrentHash(newHash);
        doc.setSignedCount(doc.getSignedCount() + 1);
        if (doc.getSignedCount() >= doc.getRequiredSigners()) {
            doc.setStatus(DocumentStatus.FULLY_SIGNED);
        }
        documentRepo.save(doc);

        logAudit(docId, userId, "SIGNATURE_ADDED",
                "Signature index " + (doc.getSignedCount() - 1) + " by " + userId);
    }

    public List<Signature> verify(UUID docId) {
        return signatureRepo.findByDocumentIdOrderBySignedAtAsc(docId);
    }

    public boolean isTampered(UUID docId) throws Exception {
        Document doc = documentRepo.findById(docId).orElseThrow();
        byte[] file = storage.load(doc.getFilePath());
        Map<String, Object> result = pkiVerificationService.verifyDocument(file);
        return !Boolean.TRUE.equals(result.get("allValid"));
    }

    public byte[] getDocumentFile(UUID docId) throws Exception {
        Document doc = documentRepo.findById(docId).orElseThrow();
        return storage.load(doc.getFilePath());
    }

    private void saveSignatureRecord(UUID docId, String userId, String prev, String hash) {
        Signature s = new Signature();
        s.setDocumentId(docId);
        s.setUserId(userId);
        s.setPreviousHash(prev);
        s.setSignatureHash(hash);
        s.setSignedAt(Instant.now());
        signatureRepo.save(s);
    }

    private void logAudit(UUID docId, String actor, String action, String details) {
        // Implementation for audit logging
    }
}
```

#### 2.7 REST Controller

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/controller/DocumentController.java`**
```java
package io.qzz.tbsciencehubproject.esignature.controller;

import io.qzz.tbsciencehubproject.esignature.model.AuditLog;
import io.qzz.tbsciencehubproject.esignature.service.SignatureService;
import io.qzz.tbsciencehubproject.esignature.service.PKIVerificationService;
import io.qzz.tbsciencehubproject.esignature.service.CertificateGenerationService;
import io.qzz.tbsciencehubproject.esignature.repository.AuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final SignatureService signatureService;
    private final PKIVerificationService pkiVerificationService;
    private final CertificateGenerationService certificateGenerationService;
    private final AuditLogRepository auditLogRepository;

    public DocumentController(SignatureService signatureService,
                             PKIVerificationService pkiVerificationService,
                             CertificateGenerationService certificateGenerationService,
                             AuditLogRepository auditLogRepository) {
        this.signatureService = signatureService;
        this.pkiVerificationService = pkiVerificationService;
        this.certificateGenerationService = certificateGenerationService;
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping("/certificates/generate")
    public ResponseEntity<Map<String, Object>> generateCertificate(
            @RequestBody Map<String, String> req) throws Exception {
        String userId = req.get("userId");
        String role = req.getOrDefault("role", "USER");
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }
        return ResponseEntity.ok(certificateGenerationService.generateCertificate(userId, role));
    }

    @PostMapping("/sign")
    public ResponseEntity<Map<String, Object>> signNew(
            @RequestParam MultipartFile file,
            @RequestParam String userId,
            @RequestParam(required = false) Integer requiredSigners) throws Exception {
        var doc = signatureService.signNew(file.getBytes(), userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "documentId", doc.getId(),
                "status", doc.getStatus(),
                "signedCount", doc.getSignedCount(),
                "requiredSigners", doc.getRequiredSigners()
        ));
    }

    @PostMapping("/{id}/sign")
    public ResponseEntity<Map<String, Object>> signNext(
            @PathVariable UUID id,
            @RequestParam String userId) throws Exception {
        signatureService.addSignature(id, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message": "Signature added successfully",
                "documentId", id.toString()
        ));
    }

    @GetMapping("/{id}/verify")
    public ResponseEntity<Map<String, Object>> verify(@PathVariable UUID id) throws Exception {
        byte[] file = signatureService.getDocumentFile(id);
        return ResponseEntity.ok(pkiVerificationService.verifyDocument(file));
    }

    @PostMapping("/verify-file")
    public ResponseEntity<Map<String, Object>> verifyFile(
            @RequestParam MultipartFile file) throws Exception {
        return ResponseEntity.ok(pkiVerificationService.verifyDocument(file.getBytes()));
    }

    @GetMapping("/{id}/tamper-check")
    public ResponseEntity<Map<String, Object>> checkTamper(@PathVariable UUID id) throws Exception {
        boolean tampered = signatureService.isTampered(id);
        return ResponseEntity.ok(Map.of(
                "documentId", id,
                "tampered", tampered,
                "message", tampered ? "Document integrity check FAILED" : "Document integrity OK"
        ));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditLog>> getAudit(@PathVariable UUID id) {
        return ResponseEntity.ok(auditLogRepository.findByDocumentIdOrderByTimestampDesc(id));
    }
}
```

---

### Phase 3: Pipeline Integration

#### 3.1 Extend Document Interface

Update the existing `Document` interface in articles-pipeline to support e-signatures:

**File: `/workspace/articles-pipeline/src/main/java/io/qzz/tbsciencehubproject/sciencehub/internal/pipeline/recipe/article/Document.java`**
```java
package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article;

import io.qzz.tbsciencehubproject.user.User;
import java.util.Collection;
import java.util.UUID;

public interface Document {
    String filename();
    Collection<User> signatures();
    void sign(User user);
    boolean hasSigned(User user);
    boolean hasSignatures(User user);
    byte[] contents();
    
    // NEW: E-signature integration methods
    UUID getEsignatureDocumentId();
    boolean isFullySigned();
    int getRequiredSignatureCount();
    int getCurrentSignatureCount();
}
```

#### 3.2 Create E-Signature Pipeline Steps

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/pipeline/GenerateCertificateStep.java`**
```java
package io.qzz.tbsciencehubproject.esignature.pipeline;

import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.AbstractPipelineStep;
import io.qzz.tbsciencehubproject.esignature.service.CertificateGenerationService;
import io.qzz.tbsciencehubproject.resource.ResourceKey;
import io.qzz.tbsciencehubproject.utils.TypeToken;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GenerateCertificateStep extends AbstractPipelineStep {
    
    private final CertificateGenerationService certificateService;
    private final ResourceKey<Map<String, Object>> certificateArtifact;

    public GenerateCertificateStep(CertificateGenerationService service) {
        super("GenerateCertificate");
        this.certificateService = service;
        this.certificateArtifact = new io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.ArtifactImpl<>(
            ResourceKey.of(new TypeToken<Map<String, Object>>() {}, "certificate"),
            this
        );
    }

    @Override
    public Collection<io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact<?>> provides() {
        return List.of(certificateArtifact);
    }

    @Override
    public Optional<io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact<?>> provided(ResourceKey<?> key) {
        if (key.equals(certificateArtifact.key())) {
            return Optional.of(certificateArtifact);
        }
        return Optional.empty();
    }

    public ResourceKey<Map<String, Object>> getCertificateArtifact() {
        return certificateArtifact.key();
    }
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/pipeline/SignDocumentStep.java`**
```java
package io.qzz.tbsciencehubproject.esignature.pipeline;

import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.AbstractPipelineStep;
import io.qzz.tbsciencehubproject.esignature.service.SignatureService;
import io.qzz.tbsciencehubproject.esignature.model.Document;
import io.qzz.tbsciencehubproject.resource.ResourceKey;
import io.qzz.tbsciencehubproject.utils.TypeToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class SignDocumentStep extends AbstractPipelineStep {
    
    private final SignatureService signatureService;
    private final ResourceKey<Document> signedDocumentArtifact;

    public SignDocumentStep(SignatureService service) {
        super("SignDocument");
        this.signatureService = service;
        this.signedDocumentArtifact = new io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.ArtifactImpl<>(
            ResourceKey.of(new TypeToken<Document>() {}, "signedDocument"),
            this
        );
    }

    @Override
    public Collection<io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact<?>> provides() {
        return List.of(signedDocumentArtifact);
    }

    @Override
    public Optional<io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact<?>> provided(ResourceKey<?> key) {
        if (key.equals(signedDocumentArtifact.key())) {
            return Optional.of(signedDocumentArtifact);
        }
        return Optional.empty();
    }

    public ResourceKey<Document> getSignedDocumentArtifact() {
        return signedDocumentArtifact.key();
    }
}
```

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/pipeline/VerifySignaturesStep.java`**
```java
package io.qzz.tbsciencehubproject.esignature.pipeline;

import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.AbstractPipelineStep;
import io.qzz.tbsciencehubproject.esignature.service.PKIVerificationService;
import io.qzz.tbsciencehubproject.resource.ResourceKey;
import io.qzz.tbsciencehubproject.utils.TypeToken;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VerifySignaturesStep extends AbstractPipelineStep {
    
    private final PKIVerificationService verificationService;
    private final ResourceKey<Map<String, Object>> verificationResultArtifact;

    public VerifySignaturesStep(PKIVerificationService service) {
        super("VerifySignatures");
        this.verificationService = service;
        this.verificationResultArtifact = new io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.ArtifactImpl<>(
            ResourceKey.of(new TypeToken<Map<String, Object>>() {}, "verificationResult"),
            this
        );
    }

    @Override
    public Collection<io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact<?>> provides() {
        return List.of(verificationResultArtifact);
    }

    @Override
    public Optional<io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact<?>> provided(ResourceKey<?> key) {
        if (key.equals(verificationResultArtifact.key())) {
            return Optional.of(verificationResultArtifact);
        }
        return Optional.empty();
    }

    public ResourceKey<Map<String, Object>> getVerificationResultArtifact() {
        return verificationResultArtifact.key();
    }
}
```

#### 3.3 Create E-Signature Pipeline

**File: `/workspace/esignature-module/src/main/java/io/qzz/tbsciencehubproject/esignature/pipeline/ESignaturePipeline.java`**
```java
package io.qzz.tbsciencehubproject.esignature.pipeline;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Pipeline;
import io.qzz.tbsciencehubproject.pipeline.pipeline.PipelineStep;
import io.qzz.tbsciencehubproject.pipeline.pipeline.RootPipelineStep;
import io.qzz.tbsciencehubproject.pipeline.validate.PipelineValidationException;
import io.qzz.tbsciencehubproject.pipeline.validate.PipelineValidator;
import io.qzz.tbsciencehubproject.utils.TypeToken;
import io.qzz.tbsciencehubproject.esignature.service.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ESignaturePipeline implements Pipeline<byte[]> {

    private final String pipelineName;
    private final Set<PipelineValidator> pipelineValidators = new CopyOnWriteArraySet<>();
    private final Set<PipelineStep> steps = new CopyOnWriteArraySet<>();
    private RootPipelineStep<byte[]> rootStep;

    public ESignaturePipeline(String pipelineName,
                              CertificateGenerationService certService,
                              SignatureService signatureService,
                              PKIVerificationService verificationService) {
        this.pipelineName = pipelineName;
        
        // Create steps
        GenerateCertificateStep certStep = new GenerateCertificateStep(certService);
        SignDocumentStep signStep = new SignDocumentStep(signatureService);
        VerifySignaturesStep verifyStep = new VerifySignaturesStep(verificationService);
        
        // Define dependencies
        signStep.dependOn(certStep.getCertificateArtifact());
        verifyStep.dependOn(signStep.getSignedDocumentArtifact());
        
        // Register steps
        registerSteps(List.of(certStep, signStep, verifyStep));
        
        // Set root step
        this.rootStep = new RootPipelineStep<>(certStep);
    }

    @Override
    public String name() {
        return pipelineName;
    }

    @Override
    public void registerValidator(PipelineValidator validator) {
        this.pipelineValidators.add(validator);
    }

    @Override
    public void validate() throws PipelineValidationException {
        for (PipelineValidator validator : pipelineValidators) {
            validator.validate(this);
        }
    }

    @Override
    public void registerStep(PipelineStep step) {
        steps.add(step);
    }

    @Override
    public void registerSteps(Collection<? extends PipelineStep> steps) {
        this.steps.addAll(steps);
    }

    @Override
    public Collection<PipelineStep> steps() {
        return List.copyOf(steps);
    }

    @Override
    public RootPipelineStep<byte[]> rootStep() {
        return rootStep;
    }

    @Override
    public TypeToken<byte[]> inputType() {
        return new TypeToken<byte[]>() {};
    }
}
```

---

### Phase 4: Application Configuration

#### 4.1 Update app/build.gradle

Add dependency on the new module:

**File: `/workspace/app/build.gradle`**
```gradle
plugins {
    id 'org.springframework.boot' version '4.0.5'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'java-common-conventions'
}

dependencies {
    implementation(project(":pipeline"))
    implementation(project(":articles-pipeline"))
    implementation(project(":esignature-module"))  // ADD THIS
    
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.flywaydb:flyway-database-postgresql'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2'
    
    // iText7 for PDF signing
    implementation 'com.itextpdf:itext7-core:7.2.5'
    implementation 'org.bouncycastle:bcprov-jdk18on:1.77'
    implementation 'org.bouncycastle:bcpkix-jdk18on:1.77'
    
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    runtimeOnly 'org.postgresql:postgresql'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'
    
    testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-flyway-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-jdbc-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-mail-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
}
```

#### 4.2 Add Configuration Properties

**File: `/workspace/app/src/main/resources/application.properties`** (or `application.yml`)
```properties
# E-Signature Configuration
esignature.storage-path=/var/app/documents
esignature.ca-path=/var/app/ca/internal-ca.p12
esignature.ca-password=changeit-ca
esignature.master-key=<GENERATE_WITH_OPENSSL_RAND_HEX_32>
esignature.default-required-signers=3

# Database (if not already configured)
spring.datasource.url=jdbc:postgresql://localhost:5432/sciencehub_db
spring.datasource.username=app_user
spring.datasource.password=secure_password
spring.jpa.hibernate.ddl-auto=update
```

#### 4.3 Create Database Migration

**File: `/workspace/app/src/main/resources/db/migration/V2__add_esignature_tables.sql`**
```sql
-- E-Signature tables
CREATE TABLE IF NOT EXISTS esign_documents (
    id UUID PRIMARY KEY,
    file_path VARCHAR(500) NOT NULL,
    current_hash VARCHAR(64) NOT NULL,
    status VARCHAR(50) NOT NULL,
    required_signers INTEGER NOT NULL,
    signed_count INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS esign_signatures (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES esign_documents(id),
    user_id VARCHAR(255) NOT NULL,
    previous_hash VARCHAR(64),
    signature_hash VARCHAR(64) NOT NULL,
    signed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS esign_user_certificates (
    user_id VARCHAR(255) PRIMARY KEY,
    encrypted_private_key TEXT NOT NULL,
    certificate_pem TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS esign_audit_logs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES esign_documents(id),
    actor_id VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address VARCHAR(45),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_esign_signatures_document_id ON esign_signatures(document_id);
CREATE INDEX idx_esign_audit_logs_document_id ON esign_audit_logs(document_id);
CREATE INDEX idx_esign_documents_status ON esign_documents(status);
```

---

## 🚀 Testing the Integration

### 1. Generate Master Key
```bash
openssl rand -hex 32
```

### 2. Run the Application
```bash
./gradlew bootRun
```

### 3. Test API Endpoints

#### Generate User Certificate
```bash
curl -X POST http://localhost:8080/api/v1/documents/certificates/generate \
  -H "Content-Type: application/json" \
  -d '{"userId": "john.doe@university.edu", "role": "PROFESSOR"}'
```

#### Sign New Document
```bash
curl -X POST http://localhost:8080/api/v1/documents/sign \
  -F "file=@article.pdf" \
  -F "userId=john.doe@university.edu" \
  -F "requiredSigners=3"
```

#### Add Additional Signature
```bash
curl -X POST http://localhost:8080/api/v1/documents/{documentId}/sign \
  -F "userId=jane.smith@university.edu"
```

#### Verify Document
```bash
curl http://localhost:8080/api/v1/documents/{documentId}/verify
```

#### Check Tampering
```bash
curl http://localhost:8080/api/v1/documents/{documentId}/tamper-check
```

#### Get Audit Log
```bash
curl http://localhost:8080/api/v1/documents/{documentId}/audit
```

---

## 🔒 Security Best Practices

1. **Master Key Management**: Store in environment variable or secrets manager
2. **CA Password**: Use secrets manager (Vault, AWS Secrets Manager)
3. **HTTPS**: Required for all API endpoints in production
4. **Access Control**: Implement authentication/authorization for signing operations
5. **Audit Logging**: Enable detailed logging for compliance
6. **File Storage**: Use secure, backed-up storage with access controls
7. **Database**: Enable SSL, use connection pooling

---

## 📊 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    ScienceHub Application                    │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────────┐  ┌───────────────┐ │
│  │   Articles   │  │  E-Signature     │  │   Pipeline    │ │
│  │   Pipeline   │◄─┤      Module      │◄─┤    Framework  │ │
│  │              │  │                  │  │               │ │
│  │ - Approve    │  │ - Cert Gen       │  │ - Runner      │ │
│  │ - Review     │  │ - Sign PDF       │  │ - Steps       │ │
│  │ - Publish    │  │ - Verify         │  │ - Artifacts   │ │
│  └──────────────┘  └──────────────────┘  └───────────────┘ │
│         │                   │                      │        │
│         └───────────────────┼──────────────────────┘        │
│                             ▼                                │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Spring Boot Controllers                  │   │
│  │  /api/v1/documents/*                                  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
         │                   │
         ▼                   ▼
┌─────────────────┐ ┌──────────────────┐
│   PostgreSQL    │ │  File Storage    │
│   Database      │ │  (Signed PDFs)   │
└─────────────────┘ └──────────────────┘
```

---

## ✅ Integration Checklist

- [ ] Create `esignature-module` directory structure
- [ ] Add `build.gradle` with dependencies
- [ ] Update `settings.gradle` to include new module
- [ ] Implement JPA entities (Document, Signature, UserCertificate, AuditLog)
- [ ] Create repositories for all entities
- [ ] Implement core services (SignatureService, DigitalSignatureService, etc.)
- [ ] Create REST controller with all endpoints
- [ ] Implement pipeline steps for e-signature workflow
- [ ] Create database migration script
- [ ] Add configuration properties
- [ ] Update app module dependencies
- [ ] Test certificate generation
- [ ] Test document signing workflow
- [ ] Test signature verification
- [ ] Test tamper detection
- [ ] Configure security settings
- [ ] Document API usage

---

## 🎯 Next Steps

1. **Implement the module** following this guide
2. **Test thoroughly** with sample documents
3. **Configure production settings** (keys, passwords, storage paths)
4. **Integrate with existing article approval pipeline**
5. **Add authentication/authorization** for signing operations
6. **Deploy and monitor** in staging environment
7. **Train users** on the new e-signing workflow

---

## 📞 Support

For questions about:
- **Pipeline Framework**: Review `/workspace/pipeline/src/main/java`
- **Article Workflows**: Review `/workspace/articles-pipeline/src/main/java`
- **E-Signature Implementation**: Refer to FEATURE_DOCUMENTATION_diff.md
- **Spring Boot Configuration**: See `/workspace/app/src/main/resources`

---

*Generated for ScienceHub E-Signature Integration | Version 1.0*
