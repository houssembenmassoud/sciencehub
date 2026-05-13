-- V1__initial_schema.sql
-- Initial schema for ScienceHub e-signature feature with PostgreSQL
-- Stores both signature metadata and document contents in the database

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Table for storing article documents directly in PostgreSQL
-- Documents are stored as BYTEA (binary data) to keep everything in one place
CREATE TABLE IF NOT EXISTS article_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id VARCHAR(255) UNIQUE NOT NULL,
    filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    document_data BYTEA NOT NULL,  -- Store actual file content in DB
    file_size BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    version INTEGER NOT NULL DEFAULT 1
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_article_documents_article_id ON article_documents(article_id);
CREATE INDEX IF NOT EXISTS idx_article_documents_status ON article_documents(status);
CREATE INDEX IF NOT EXISTS idx_article_documents_created_at ON article_documents(created_at DESC);

-- Comments for documentation
COMMENT ON TABLE article_documents IS 'Stores article documents with binary content in PostgreSQL';
COMMENT ON COLUMN article_documents.article_id IS 'Unique business identifier for the article';
COMMENT ON COLUMN article_documents.document_data IS 'Binary content of the document (PDF, DOCX, etc.)';
COMMENT ON COLUMN article_documents.status IS 'Document workflow status: PENDING, SIGNED, APPROVED, REJECTED';

-- Table for storing e-signature metadata with PKI support
CREATE TABLE IF NOT EXISTS article_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    document_hash VARCHAR(64) NOT NULL,
    signature_value TEXT,
    certificate_data TEXT,
    signed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500)
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

-- Function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger to auto-update timestamp on article_documents
DROP TRIGGER IF EXISTS update_article_documents_updated_at ON article_documents;
CREATE TRIGGER update_article_documents_updated_at
    BEFORE UPDATE ON article_documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
