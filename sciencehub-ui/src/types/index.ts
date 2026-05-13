export interface User {
  id: number;
  name: string;
  email: string;
  role: 'AUTHOR' | 'REVIEWER' | 'ADMIN';
}

export interface Article {
  id: number;
  title: string;
  abstract: string;
  content: string;
  status: 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'SIGNED';
  authorId: number;
  authorName?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Signature {
  id: number;
  articleId: number;
  signerId: number;
  signerName: string;
  signedAt: string;
  signatureValue: string;
  documentHash: string;
  ipAddress?: string;
  userAgent?: string;
  reason?: string;
  location?: string;
  isValid?: boolean;
}

export interface Certificate {
  id: number;
  userId: number;
  userName: string;
  publicKey: string;
  certificate: string;
  issuedAt: string;
  expiresAt: string;
  serialNumber: string;
}

export interface SignatureMetadata {
  id: number;
  articleId: number;
  signerId: number;
  signerName: string;
  signedAt: string;
  documentHash: string;
  ipAddress?: string;
  userAgent?: string;
  reason?: string;
  location?: string;
}

export interface VerificationResult {
  isValid: boolean;
  signatures: Array<{
    signerName: string;
    signedAt: string;
    isValid: boolean;
    message: string;
  }>;
  documentHash: string;
  verifiedAt: string;
}

export interface PipelineStatus {
  pipelineId: string;
  status: 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  currentStep?: string;
  completedSteps: string[];
  failedStep?: string;
  errorMessage?: string;
  startedAt?: string;
  completedAt?: string;
}
