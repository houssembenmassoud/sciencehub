-- V2__add_signature_metadata.sql
-- Creates table for storing e-signature metadata with PKI support
-- Enhanced to include certificate data and audit information

-- Table for storing e-signature metadata with PKI support
CREATE TABLE IF NOT EXISTS article_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    document_hash VARCHAR(64) NOT NULL,
    signature_value TEXT,
    certificate_data TEXT,  -- Base64-encoded X.509 certificate used for signing
    signed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(500),
    ip_address VARCHAR(45),  -- IPv4 or IPv6 address of signer
    user_agent VARCHAR(500)  -- Browser/client user agent for audit trail
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_article_signatures_article_id ON article_signatures(article_id);
CREATE INDEX IF NOT EXISTS idx_article_signatures_user_id ON article_signatures(user_id);
CREATE INDEX IF NOT EXISTS idx_article_signatures_signed_at ON article_signatures(signed_at ASC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_article_user_unique ON article_signatures(article_id, user_id);

-- Comments for documentation
COMMENT ON TABLE article_signatures IS 'Stores e-signature metadata for article approval workflow';
COMMENT ON COLUMN article_signatures.article_id IS 'Reference to article (no FK - uses existing system)';
COMMENT ON COLUMN article_signatures.user_id IS 'Reference to user by name (no FK - uses existing system)';
COMMENT ON COLUMN article_signatures.document_hash IS 'SHA-256 hash of document at signing time';
COMMENT ON COLUMN article_signatures.signature_value IS 'Base64-encoded cryptographic signature (PKI)';
COMMENT ON COLUMN article_signatures.certificate_data IS 'Base64-encoded X.509 certificate used for signing';
COMMENT ON COLUMN article_signatures.signed_at IS 'Timestamp when signature was created';
COMMENT ON COLUMN article_signatures.reason IS 'Optional reason for signing';
COMMENT ON COLUMN article_signatures.ip_address IS 'IP address of the signer for audit trail';
COMMENT ON COLUMN article_signatures.user_agent IS 'User agent string for audit trail';

-- Table for storing user certificates and keys persistently
CREATE TABLE IF NOT EXISTS user_certificates (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) UNIQUE NOT NULL,
    user_name VARCHAR(500) NOT NULL,
    private_key_data TEXT NOT NULL,  -- PEM-encoded private key
    certificate_data TEXT NOT NULL,  -- PEM-encoded X.509 certificate
    public_key_data TEXT NOT NULL,   -- PEM-encoded public key
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1
);

-- Indexes for certificate lookup
CREATE INDEX IF NOT EXISTS idx_user_certificates_user_id ON user_certificates(user_id);
CREATE INDEX IF NOT EXISTS idx_user_certificates_expires_at ON user_certificates(expires_at);
CREATE INDEX IF NOT EXISTS idx_user_certificates_active ON user_certificates(is_active) WHERE is_active = TRUE;

-- Comments for certificate table
COMMENT ON TABLE user_certificates IS 'Stores user certificates and keys for PKI e-signing';
COMMENT ON COLUMN user_certificates.user_id IS 'Unique identifier for user (from existing system)';
COMMENT ON COLUMN user_certificates.user_name IS 'Display name of user';
COMMENT ON COLUMN user_certificates.private_key_data IS 'PEM-encoded RSA private key (encrypted at rest in production)';
COMMENT ON COLUMN user_certificates.certificate_data IS 'PEM-encoded X.509 certificate signed by ScienceHub CA';
COMMENT ON COLUMN user_certificates.public_key_data IS 'PEM-encoded RSA public key';
COMMENT ON COLUMN user_certificates.created_at IS 'Certificate creation timestamp';
COMMENT ON COLUMN user_certificates.expires_at IS 'Certificate expiration timestamp';
COMMENT ON COLUMN user_certificates.is_active IS 'Whether certificate is currently active';
