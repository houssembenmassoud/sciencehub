import axios from 'axios'
import type { 
  Article, 
  Signature, 
  Certificate, 
  VerificationResult, 
  PipelineStatus,
  User 
} from '@/types'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor to add auth token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('auth_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('auth_token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export const articleService = {
  getAll: () => api.get<Article[]>('/articles'),
  getById: (id: number) => api.get<Article>(`/articles/${id}`),
  create: (article: Partial<Article>) => api.post<Article>('/articles', article),
  update: (id: number, article: Partial<Article>) => api.put<Article>(`/articles/${id}`, article),
  delete: (id: number) => api.delete(`/articles/${id}`),
  getDocument: (id: number) => api.get(`/articles/${id}/document`, { responseType: 'blob' })
}

export const signatureService = {
  sign: (articleId: number, reason?: string, location?: string) => 
    api.post<Signature>(`/signatures/sign/${articleId}`, { reason, location }),
  
  verify: (articleId: number) => 
    api.get<VerificationResult>(`/signatures/verify/${articleId}`),
  
  getAll: (articleId?: number) => {
    const url = articleId ? `/signatures?articleId=${articleId}` : '/signatures'
    return api.get<Signature[]>(url)
  },
  
  verifySingle: (signatureValue: string, documentHash: string, publicKey: string) =>
    api.post<{ isValid: boolean; message: string }>('/signatures/verify-single', {
      signatureValue,
      documentHash,
      publicKey
    }),
  
  exportCertificate: () => 
    api.get<Blob>('/signatures/certificate/export', { responseType: 'blob' })
}

export const certificateService = {
  getCurrentUserCertificate: () => api.get<Certificate>('/certificates/me'),
  getUserCertificate: (userId: number) => api.get<Certificate>(`/certificates/user/${userId}`),
  regenerate: () => api.post<Certificate>('/certificates/regenerate'),
  download: (userId: number) => 
    api.get<Blob>(`/certificates/${userId}/download`, { responseType: 'blob' })
}

export const pipelineService = {
  startApproval: (articleId: number) => 
    api.post<PipelineStatus>(`/pipeline/approval/start/${articleId}`),
  
  getStatus: (articleId: number) => 
    api.get<PipelineStatus>(`/pipeline/approval/status/${articleId}`),
  
  cancel: (articleId: number) => 
    api.post<PipelineStatus>(`/pipeline/approval/cancel/${articleId}`)
}

export const authService = {
  login: (email: string, password: string) => 
    api.post<{ token: string; user: User }>('/auth/login', { email, password }),
  
  logout: () => api.post('/auth/logout'),
  
  getCurrentUser: () => api.get<User>('/auth/me'),
  
  register: (user: Partial<User> & { password: string }) => 
    api.post<User>('/auth/register', user)
}

export default api
