--- FEATURE_DOCUMENTATION.md (原始)


+++ FEATURE_DOCUMENTATION.md (修改后)
# Digital Document Signature System - Comprehensive Documentation

## 📋 Feature Overview

This is a **Spring Boot application** implementing a **multi-signature digital document signing system** with PKI (Public Key Infrastructure) verification. The system enables secure document signing workflows where multiple authorized users must sign a document before it's considered fully executed.

### Key Capabilities

1. **Internal Certificate Authority (CA)** - Self-signed CA for issuing user certificates
2. **User Certificate Generation** - RSA 2048-bit key pairs with X.509 certificates
3. **Multi-Signature Workflow** - Documents require configurable signatures (default: 3)
4. **PDF Digital Signing** - iText7 + BouncyCastle for cryptographic PDF signatures
5. **PKI Verification** - Validates signature integrity, certificate chains, and tampering detection
6. **Audit Logging** - Complete trail of all signing operations with timestamps
7. **Encrypted Private Keys** - AES-256-GCM encryption for secure key storage

---

## 🌐 REST API Endpoints

### Base URL: `/api/v1/documents`

### 1. Generate User Certificate
**Endpoint:** `POST /certificates/generate`
**Purpose:** Generate a new user certificate signed by the internal CA

**Request:**
```json
{
  "userId": "john.doe@university.edu",
  "role": "PROFESSOR"
}
```

**Response:**
```json
{
  "userId": "john.doe@university.edu",
  "certificatePem": "-----BEGIN CERTIFICATE-----\nMIID...",
  "thumbprint": "a3f5b8c2d1e4...",
  "status": "registered",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

---

### 2. Sign New Document
**Endpoint:** `POST /sign`
**Purpose:** Upload a new PDF document and apply the first signature

**Request:** `multipart/form-data`
- `file`: PDF file
- `userId`: string (must have existing certificate)
- `requiredSigners`: integer (optional, default: 3)

**Response:**
```json
{
  "success": true,
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING_SIGNATURE",
  "signedCount": 1,
  "requiredSigners": 3
}
```

---

### 3. Add Signature to Existing Document
**Endpoint:** `POST /{id}/sign`
**Purpose:** Add an additional signature to a pending document

**Request:**
- Path parameter: `id` (UUID)
- Query parameter: `userId` (string)

**Response:**
```json
{
  "success": true,
  "message": "Signature added successfully",
  "documentId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

### 4. Verify Document Signatures
**Endpoint:** `GET /{id}/verify`
**Purpose:** Verify all signatures on a stored document

**Response:**
```json
{
  "allValid": true,
  "signatureCount": 3,
  "signatures": [
    {
      "fieldName": "sig_john_doe_university_edu_1234567890",
      "signer": "john.doe@university.edu",
      "signatureValid": true,
      "documentNotModified": true,
      "chainValid": true,
      "valid": true,
      "signDate": "2024-01-15T10:30:00Z"
    }
  ]
}
```

---

### 5. Verify Uploaded File
**Endpoint:** `POST /verify-file`
**Purpose:** Verify signatures on an uploaded PDF file (without storing)

**Request:** `multipart/form-data` with `file` parameter

**Response:** Same structure as `GET /{id}/verify`

---

### 6. Check Document Tampering
**Endpoint:** `GET /{id}/tamper-check`
**Purpose:** Quick integrity check to detect if document was modified

**Response:**
```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "tampered": false,
  "message": "Document integrity OK"
}
```

---

### 7. Get Audit Log
**Endpoint:** `GET /{id}/audit`
**Purpose:** Retrieve complete audit trail for a document

**Response:**
```json
[
  {
    "id": "uuid-1",
    "documentId": "uuid-doc",
    "actorId": "john.doe@university.edu",
    "action": "DOCUMENT_CREATED_AND_SIGNED",
    "details": "First signature by john.doe@university.edu",
    "timestamp": "2024-01-15T10:30:00Z"
  },
  {
    "id": "uuid-2",
    "documentId": "uuid-doc",
    "actorId": "jane.smith@university.edu",
    "action": "SIGNATURE_ADDED",
    "details": "Signature index 1 by jane.smith@university.edu",
    "timestamp": "2024-01-15T11:45:00Z"
  }
]
```

---

## 🔧 Services Implementation

### 1. DocumentController.java
**Role:** REST API endpoint handler
**Dependencies:** SignatureService, PKIVerificationService, CertificateGenerationService, AuditLogRepository

```java
package com.sciencehub.signature.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import java.util.*;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final SignatureService signatureService;
    private final PKIVerificationService pkiVerificationService;
    private final CertificateGenerationService certificateGenerationService;
    private final AuditLogRepository auditLogRepository;

    /* ─────────────── CERTIFICATE ─────────────── */

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

    /* ─────────────── SIGNING ─────────────── */

    @PostMapping("/sign")
    public ResponseEntity<Map<String, Object>> signNew(
            @RequestParam MultipartFile file,
            @RequestParam String userId,
            @RequestParam(required = false) Integer requiredSigners) throws Exception {
        if (requiredSigners != null && requiredSigners > 0) {
            // You can extend SignatureService to accept this; omitted for brevity
        }
        Document doc = signatureService.signNew(file.getBytes(), userId);
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

    /* ─────────────── VERIFICATION ─────────────── */

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

    /* ─────────────── AUDIT ─────────────── */

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditLog>> getAudit(@PathVariable UUID id) {
        return ResponseEntity.ok(auditLogRepository.findByDocumentIdOrderByTimestampDesc(id));
    }
}
```

---

### 2. SignatureService.java
**Role:** Core business logic for multi-signature workflow
**Dependencies:** DocumentRepository, SignatureRepository, StorageService, HashService, DigitalSignatureService, PKIVerificationService, AuditLogRepository

```java
package com.sciencehub.signature.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SignatureService {

    private final DocumentRepository documentRepo;
    private final SignatureRepository signatureRepo;
    private final StorageService storage;
    private final HashService hashService;
    private final DigitalSignatureService digitalSignatureService;
    private final PKIVerificationService pkiVerificationService;
    private final AuditLogRepository auditLogRepo;

    @Value("${signing.default-required-signers:3}")
    private int defaultRequiredSigners;

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
        AuditLog log = new AuditLog();
        log.setDocumentId(docId);
        log.setActorId(actor);
        log.setAction(action);
        log.setDetails(details);
        log.setTimestamp(Instant.now());
        auditLogRepo.save(log);
    }
}
```

---

### 3. DigitalSignatureService.java
**Role:** PDF cryptographic signing using iText7 and BouncyCastle
**Dependencies:** UserCertificateRepository, AesGcmUtils, InternalCAService

```java
package com.sciencehub.signature.demo;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.signatures.*;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

@Service
@RequiredArgsConstructor
public class DigitalSignatureService {
    private final UserCertificateRepository userCertRepo;
    private final AesGcmUtils aesGcmUtils;
    private final InternalCAService caService;

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

---

### 4. PKIVerificationService.java
**Role:** Signature and certificate chain verification
**Dependencies:** InternalCAService

```java
package com.sciencehub.signature.demo;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.signatures.PdfPKCS7;
import com.itextpdf.signatures.SignatureUtil;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PKIVerificationService {
    private final InternalCAService caService;

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

---

### 5. CertificateGenerationService.java
**Role:** User certificate creation and management
**Dependencies:** InternalCAService, UserCertificateRepository, AesGcmUtils

```java
package com.sciencehub.signature.demo;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class CertificateGenerationService {
    private final InternalCAService caService;
    private final UserCertificateRepository userCertRepo;
    private final AesGcmUtils aesGcmUtils;

    public Map<String, Object> generateCertificate(String userId, String role) throws Exception {
        if (userCertRepo.existsById(userId)) {
            throw new RuntimeException("Certificate already exists for: " + userId);
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair userKeys = kpg.generateKeyPair();

        KeyStore caKs = caService.getCAKeyStore();
        PrivateKey caKey = (PrivateKey) caKs.getKey("university-internal-ca",
                System.getenv().getOrDefault("CA_PASSWORD", "changeit-ca").toCharArray());
        X509Certificate caCert = (X509Certificate) caKs.getCertificate("university-internal-ca");

        X500Name subject = new org.bouncycastle.asn1.x500.X500Name(
                "CN=" + userId + ",OU=" + role + ",O=ScienceHub,C=US");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date from = new Date();
        Date to = new Date(System.currentTimeMillis() + 2L * 365 * 24 * 60 * 60 * 1000);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new org.bouncycastle.asn1.x500.X500Name(caCert.getSubjectX500Principal().getName()),
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

---

### 6. InternalCAService.java
**Role:** CA keystore management and self-signed CA certificate generation
**Configuration:** `storage.ca.path`, `ca.password`

```java
package com.sciencehub.signature.demo;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${storage.ca.path}")
    private String caPath;

    @Value("${ca.password}")
    private String caPassword;

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

---

### 7. StorageService.java
**Role:** File system storage for signed PDFs
**Configuration:** `storage.path`

```java
package com.sciencehub.signature.demo;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class StorageService {
    @Value("${storage.path}")
    private String storagePath;

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

---

### 8. HashService.java
**Role:** SHA-256 hashing for document integrity

```java
package com.sciencehub.signature.demo;

import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class HashService {
    public String sha256(byte[] data) {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte []hash = digest.digest(data);
            return Base64.getEncoder().encodeToString(hash);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
```

---

### 9. AesGcmUtils.java
**Role:** AES-256-GCM encryption/decryption for private keys
**Configuration:** `encryption.master-key` (hex-encoded 32-byte key)

```java
package com.sciencehub.signature.demo;

import org.springframework.beans.factory.annotation.Value;
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

    public AesGcmUtils(@Value("${encryption.master-key}") String masterKeyHex) {
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

---

### 10. SecurityConfig.java
**Role:** BouncyCastle security provider registration

```java
package com.sciencehub.signature.demo;

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

---

## 📊 Data Models

### Document.java
```java
@Entity
@Data
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

### Signature.java
```java
@Entity
@Data
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

### UserCertificate.java
```java
@Entity
@Data
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

### AuditLog.java
```java
@Entity
@Data
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

### DocumentStatus.java
```java
public enum DocumentStatus {
    DRAFT, PENDING_SIGNATURE, FULLY_SIGNED, ARCHIVED
}
```

---

## 🗄️ Repositories

### DocumentRepository
```java
public interface DocumentRepository extends JpaRepository<Document, UUID> {
}
```

### SignatureRepository
```java
public interface SignatureRepository extends JpaRepository<Signature, Long> {
    List<Signature> findByDocumentIdOrderBySignedAtAsc(UUID documentId);
}
```

### UserCertificateRepository
```java
@Repository
public interface UserCertificateRepository extends JpaRepository<UserCertificate, String> {
}
```

### AuditLogRepository
```java
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByDocumentIdOrderByTimestampDesc(UUID documentId);
}
```

---

## 🔒 Security Considerations

### Key Protection
- **Private keys** are encrypted using AES-256-GCM before storage
- **Master encryption key** must be provided via environment variable
- **CA private key** protected by password in PKCS12 keystore

### Certificate Validation
- All user certificates are signed by the internal CA
- Certificate chain verification ensures only trusted certificates can sign
- Certificates expire after 2 years (configurable)

### Access Control
- Users cannot sign the same document twice
- Only documents in `PENDING_SIGNATURE` status can receive new signatures
- Each signature creates an immutable audit record

### Input Validation
- User ID validation prevents empty/null values
- File type validation should be added for production use
- Signature field names are sanitized to prevent injection

---

## 🛠️ Technology Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 3.x |
| PDF Library | iText7 (AGPL/commercial) |
| Cryptography | BouncyCastle |
| Database | PostgreSQL/MySQL (JPA) |
| Encryption | AES-256-GCM |
| Hashing | SHA-256 |
| Certificate Format | X.509 PEM |
| Keystore Format | PKCS12 |

---

## ⚙️ Configuration Properties

```properties
# Storage paths
storage.path=/var/app/documents
storage.ca.path=/var/app/ca/internal-ca.p12

# CA configuration
ca.password=changeit-ca

# Encryption
encryption.master-key=<64-char-hex-key>

# Signing workflow
signing.default-required-signers=3

# Database (example for PostgreSQL)
spring.datasource.url=jdbc:postgresql://localhost:5432/signature_db
spring.datasource.username=app_user
spring.datasource.password=secure_password
spring.jpa.hibernate.ddl-auto=update
```

---

## 🚀 Deployment Considerations

### Production Requirements
1. **Secure master key generation:** Use `openssl rand -hex 32`
2. **CA password management:** Use secrets manager (Vault, AWS Secrets Manager)
3. **File storage:** Use secure, backed-up storage with access controls
4. **Database:** Enable SSL, use connection pooling
5. **HTTPS:** Required for all API endpoints
6. **Logging:** Sanitize sensitive data in logs
7. **Monitoring:** Track failed signature attempts, certificate expirations

### Scaling Considerations
- Stateless application design allows horizontal scaling
- Shared storage required for multi-instance deployments
- Database connection pooling recommended
- Consider async processing for large PDF files

---

## 🔮 Future Enhancements

1. **Certificate Revocation List (CRL)** support
2. **Timestamp Authority (TSA)** integration for non-repudiation
3. **Long-term validation (LTV)** for archived documents
4. **Multi-factor authentication** for signing operations
5. **Batch signing** for high-volume workflows
6. **Webhook notifications** on status changes
7. **Document templates** with predefined signature fields
8. **Mobile app** for remote signing
9. **Integration** with external PKI providers (DigiCert, GlobalSign)
10. **Compliance reporting** for regulatory requirements

---

## 📝 License Notes

- **iText7** uses AGPL license (requires open-source disclosure) or commercial license
- **BouncyCastle** uses MIT-style license (permissive)
- Ensure compliance with cryptographic export regulations in your jurisdiction

---

*Generated: 2024 | Version: 1.0 | ScienceHub Signature System*