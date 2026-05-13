import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Article, Signature, VerificationResult, PipelineStatus } from '@/types'
import { articleService, signatureService, pipelineService } from '@/services/api'

export const useArticlesStore = defineStore('articles', () => {
  const articles = ref<Article[]>([])
  const currentArticle = ref<Article | null>(null)
  const signatures = ref<Signature[]>([])
  const verificationResult = ref<VerificationResult | null>(null)
  const pipelineStatus = ref<PipelineStatus | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const signedArticles = computed(() => 
    articles.value.filter(a => a.status === 'SIGNED' || a.status === 'APPROVED')
  )

  const pendingArticles = computed(() => 
    articles.value.filter(a => a.status === 'PENDING_REVIEW' || a.status === 'DRAFT')
  )

  async function fetchAll() {
    loading.value = true
    error.value = null
    
    try {
      const response = await articleService.getAll()
      articles.value = response.data
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to fetch articles'
    } finally {
      loading.value = false
    }
  }

  async function fetchById(id: number) {
    loading.value = true
    error.value = null
    
    try {
      const response = await articleService.getById(id)
      currentArticle.value = response.data
      return currentArticle.value
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to fetch article'
      return null
    } finally {
      loading.value = false
    }
  }

  async function create(article: Partial<Article>) {
    loading.value = true
    error.value = null
    
    try {
      const response = await articleService.create(article)
      articles.value.unshift(response.data)
      return { success: true, article: response.data }
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to create article'
      return { success: false, error: error.value }
    } finally {
      loading.value = false
    }
  }

  async function update(id: number, article: Partial<Article>) {
    loading.value = true
    error.value = null
    
    try {
      const response = await articleService.update(id, article)
      const index = articles.value.findIndex(a => a.id === id)
      if (index !== -1) {
        articles.value[index] = response.data
      }
      if (currentArticle.value?.id === id) {
        currentArticle.value = response.data
      }
      return { success: true, article: response.data }
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to update article'
      return { success: false, error: error.value }
    } finally {
      loading.value = false
    }
  }

  async function deleteArticle(id: number) {
    loading.value = true
    error.value = null
    
    try {
      await articleService.delete(id)
      articles.value = articles.value.filter(a => a.id !== id)
      return { success: true }
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to delete article'
      return { success: false, error: error.value }
    } finally {
      loading.value = false
    }
  }

  async function fetchSignatures(articleId?: number) {
    loading.value = true
    error.value = null
    
    try {
      const response = await signatureService.getAll(articleId)
      signatures.value = response.data
      return signatures.value
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to fetch signatures'
      return []
    } finally {
      loading.value = false
    }
  }

  async function signArticle(articleId: number, reason?: string, location?: string) {
    loading.value = true
    error.value = null
    
    try {
      const response = await signatureService.sign(articleId, reason, location)
      signatures.value.push(response.data)
      
      // Update article status if needed
      const articleIndex = articles.value.findIndex(a => a.id === articleId)
      if (articleIndex !== -1) {
        articles.value[articleIndex].status = 'SIGNED'
      }
      
      return { success: true, signature: response.data }
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to sign article'
      return { success: false, error: error.value }
    } finally {
      loading.value = false
    }
  }

  async function verifyArticle(articleId: number) {
    loading.value = true
    error.value = null
    
    try {
      const response = await signatureService.verify(articleId)
      verificationResult.value = response.data
      return { success: true, result: response.data }
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to verify article'
      return { success: false, error: error.value }
    } finally {
      loading.value = false
    }
  }

  async function startApprovalPipeline(articleId: number) {
    loading.value = true
    error.value = null
    
    try {
      const response = await pipelineService.startApproval(articleId)
      pipelineStatus.value = response.data
      return { success: true, status: response.data }
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to start approval pipeline'
      return { success: false, error: error.value }
    } finally {
      loading.value = false
    }
  }

  async function getPipelineStatus(articleId: number) {
    loading.value = true
    error.value = null
    
    try {
      const response = await pipelineService.getStatus(articleId)
      pipelineStatus.value = response.data
      return response.data
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to get pipeline status'
      return null
    } finally {
      loading.value = false
    }
  }

  async function downloadDocument(articleId: number) {
    try {
      const response = await articleService.getDocument(articleId)
      const blob = new Blob([response.data], { type: 'application/pdf' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `article-${articleId}.pdf`
      link.click()
      window.URL.revokeObjectURL(url)
      return { success: true }
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to download document'
      return { success: false, error: error.value }
    }
  }

  return {
    articles,
    currentArticle,
    signatures,
    verificationResult,
    pipelineStatus,
    loading,
    error,
    signedArticles,
    pendingArticles,
    fetchAll,
    fetchById,
    create,
    update,
    deleteArticle,
    fetchSignatures,
    signArticle,
    verifyArticle,
    startApprovalPipeline,
    getPipelineStatus,
    downloadDocument
  }
})
