package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * Service for generating and managing digital certificates for users.
 * Integrates with existing User interface - no duplication.
 * Stores certificates persistently in PostgreSQL database.
 */
@Service
@Transactional
public class CertificateService {
    
    private final UserCertificateRepository certificateRepository;
    
    private KeyPair caKeyPair;
    private X509Certificate caCertificate;
    
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    
    public CertificateService(UserCertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }
    
    @PostConstruct
    public void init() throws NoSuchAlgorithmException, OperatorCreationException, CertificateException {
        // Initialize CA (Certificate Authority) for signing user certificates
        initializeCA();
    }
    
    /**
     * Initialize a self-signed CA certificate for issuing user certificates
     */
    private void initializeCA() throws NoSuchAlgorithmException, OperatorCreationException, CertificateException {
KeyPairGenerator keyPairGenerator;
try {
    keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
    throw new RuntimeException("Failed to initialize RSA with Bouncy Castle", e);
}        keyPairGenerator.initialize(2048);
        caKeyPair = keyPairGenerator.generateKeyPair();
        
        // Create self-signed CA certificate
        X500Name issuer = new X500Name("CN=ScienceHub CA, O=ScienceHub, C=US");
        X500Name subject = issuer; // Self-signed
        
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(System.currentTimeMillis()),
            new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30), // Valid from 30 days ago
            new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10), // Valid for 10 years
            subject,
            caKeyPair.getPublic()
        );
        
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        
        caCertificate = new JcaX509CertificateConverter().getCertificate(certHolder);
    }
    
    /**
     * Generate or retrieve a key pair for a user from database
     */
    @Transactional(readOnly = true)
    public KeyPair getUserKeyPair(String userId) throws Exception {
        UserCertificateEntity entity = certificateRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("Certificate not found for user: " + userId));
        
        // Decode private key from PEM
        String privateKeyPEM = entity.getPrivateKeyData()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        
        // Decode public key from PEM
        String publicKeyPEM = entity.getPublicKeyData()
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(pubKeySpec);
        
        return new KeyPair(publicKey, privateKey);
    }
    
    /**
     * Generate or retrieve a certificate for a user
     */
    public X509Certificate getUserCertificate(String userId, String userName) 
            throws Exception {
        
        // Check if certificate exists in database
        UserCertificateEntity existingEntity = certificateRepository.findActiveByUserId(userId).orElse(null);
        
        if (existingEntity != null && existingEntity.getExpiresAt().isAfter(java.time.Instant.now())) {
            // Return existing valid certificate
            String certPEM = existingEntity.getCertificateData()
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
            
            byte[] certBytes = Base64.getDecoder().decode(certPEM);
            return new JcaX509CertificateConverter().getCertificate(
                new X509CertificateHolder(certBytes)
            );
        }
        
        // Generate new certificate
        return generateNewCertificate(userId, userName);
    }
    
    /**
     * Generate a new certificate for user and save to database
     */
    private X509Certificate generateNewCertificate(String userId, String userName) 
            throws Exception {
        
        // Generate new key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(2048);
        KeyPair userKeyPair = keyPairGenerator.generateKeyPair();
        
        // Create certificate for user
        X500Name issuer = new X500Name("CN=ScienceHub CA, O=ScienceHub, C=US");
        X500Name subject = new X500Name("CN=" + userName + ", UID=" + userId + ", O=ScienceHub");
        
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365); // 1 year
        
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(System.currentTimeMillis()),
            notBefore,
            notAfter,
            subject,
            userKeyPair.getPublic()
        );
        
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(certHolder);
        
        // Save to database
        UserCertificateEntity entity = new UserCertificateEntity();
        entity.setUserId(userId);
        entity.setUserName(userName);
        entity.setPrivateKeyData(encodePrivateKey(userKeyPair.getPrivate()));
        entity.setPublicKeyData(encodePublicKey(userKeyPair.getPublic()));
        entity.setCertificateData(encodeCertificate(certificate));
        entity.setCreatedAt(java.time.Instant.now());
        entity.setExpiresAt(java.time.Instant.ofEpochMilli(notAfter.getTime()));
        entity.setIsActive(true);
        
        certificateRepository.save(entity);
        
        return certificate;
    }
    
    /**
     * Sign data with user's private key
     */
    public byte[] signData(String userId, byte[] data) throws Exception {
        KeyPair keyPair = getUserKeyPair(userId);
        
        Signature signature = Signature.getInstance("SHA256withRSA", "BC");
        signature.initSign(keyPair.getPrivate());
        signature.update(data);
        
        return signature.sign();
    }
    
    /**
     * Verify a signature using user's public key
     */
    public boolean verifySignature(String userId, byte[] data, byte[] signatureBytes) throws Exception {
        KeyPair keyPair = getUserKeyPair(userId);
        
        Signature signature = Signature.getInstance("SHA256withRSA", "BC");
        signature.initVerify(keyPair.getPublic());
        signature.update(data);
        
        return signature.verify(signatureBytes);
    }
    
    /**
     * Verify a signature using user's certificate
     */
    public boolean verifySignatureWithCert(String userId, byte[] data, byte[] signatureBytes) throws Exception {
        X509Certificate cert = getUserCertificate(userId, userId);
        
        Signature signature = Signature.getInstance("SHA256withRSA", "BC");
        signature.initVerify(cert);
        signature.update(data);
        
        return signature.verify(signatureBytes);
    }
    
    /**
     * Export certificate as PEM string
     */
    public String exportCertificate(String userId, String userName) throws Exception {
        X509Certificate cert = getUserCertificate(userId, userName);
        return encodeCertificate(cert);
    }
    
    /**
     * Encode certificate to PEM format
     */
    private String encodeCertificate(X509Certificate cert) throws Exception {
        Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());
        return "-----BEGIN CERTIFICATE-----\n" +
               encoder.encodeToString(cert.getEncoded()) +
               "\n-----END CERTIFICATE-----";
    }
    
    /**
     * Encode private key to PEM format
     */
    private String encodePrivateKey(PrivateKey key) throws Exception {
        Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());
        return "-----BEGIN PRIVATE KEY-----\n" +
               encoder.encodeToString(key.getEncoded()) +
               "\n-----END PRIVATE KEY-----";
    }
    
    /**
     * Encode public key to PEM format
     */
    private String encodePublicKey(PublicKey key) throws Exception {
        Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());
        return "-----BEGIN PUBLIC KEY-----\n" +
               encoder.encodeToString(key.getEncoded()) +
               "\n-----END PUBLIC KEY-----";
    }
    
    /**
     * Get CA certificate for verification chain
     */
    public X509Certificate getCACertificate() {
        return caCertificate;
    }
}
