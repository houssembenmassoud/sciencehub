<template>
  <div v-if="article" class="space-y-6">
    <!-- Header -->
    <div class="flex items-start justify-between">
      <div>
        <button @click="$router.back()" class="text-gray-500 hover:text-gray-700 mb-2">
          <i class="pi pi-arrow-left mr-2"></i>Back
        </button>
        <h1 class="text-3xl font-bold text-gray-900">{{ article.title }}</h1>
        <div class="flex items-center gap-4 mt-2 text-gray-500">
          <span><i class="pi pi-user mr-1"></i>{{ article.authorName }}</span>
          <span><i class="pi pi-calendar mr-1"></i>{{ formatDate(article.createdAt) }}</span>
          <span 
            class="status-badge"
            :class="getStatusClass(article.status)"
          >
            {{ article.status.replace('_', ' ') }}
          </span>
        </div>
      </div>
      <div class="flex gap-2">
        <button @click="downloadDocument" class="btn-secondary">
          <i class="pi pi-download mr-2"></i>Download
        </button>
        <button @click="showSignModal = true" class="btn-success">
          <i class="pi pi-signature mr-2"></i>Sign
        </button>
      </div>
    </div>

    <!-- Abstract -->
    <div class="card">
      <h2 class="text-xl font-semibold text-gray-900 mb-4">Abstract</h2>
      <p class="text-gray-700 leading-relaxed">{{ article.abstract }}</p>
    </div>

    <!-- Content -->
    <div class="card">
      <h2 class="text-xl font-semibold text-gray-900 mb-4">Content</h2>
      <div class="prose max-w-none">
        <p class="text-gray-700 whitespace-pre-line">{{ article.content }}</p>
      </div>
    </div>

    <!-- Signatures Panel -->
    <div class="card">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-semibold text-gray-900">Signatures</h2>
        <button @click="verifySignatures" class="btn-primary">
          <i class="pi pi-check-circle mr-2"></i>Verify All
        </button>
      </div>

      <div v-if="articlesStore.loading" class="text-center py-8">
        <i class="pi pi-spin pi-spinner text-4xl text-gray-400"></i>
      </div>

      <div v-else-if="signatures.length === 0" class="text-center py-8 text-gray-500">
        <i class="pi pi-signature text-4xl mb-3"></i>
        <p>No signatures yet</p>
      </div>

      <div v-else class="space-y-4">
        <div 
          v-for="sig in signatures" 
          :key="sig.id"
          class="signature-panel"
        >
          <div class="flex items-start justify-between">
            <div>
              <div class="flex items-center gap-2">
                <i class="pi pi-check-circle text-green-600"></i>
                <span class="font-semibold text-gray-900">{{ sig.signerName }}</span>
              </div>
              <p class="text-sm text-gray-600 mt-1">
                Signed on {{ formatDate(sig.signedAt) }}
              </p>
              <p v-if="sig.reason" class="text-sm text-gray-600 mt-1">
                Reason: {{ sig.reason }}
              </p>
            </div>
            <span class="status-badge status-approved">Valid</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Verification Result -->
    <div v-if="verificationResult" class="card">
      <h2 class="text-xl font-semibold text-gray-900 mb-4">Verification Result</h2>
      <div :class="verificationResult.isValid ? 'bg-green-50 border-green-200' : 'bg-red-50 border-red-200'" 
           class="border rounded-lg p-4">
        <div class="flex items-center gap-3">
          <i :class="verificationResult.isValid ? 'pi pi-check-circle text-green-600' : 'pi pi-times-circle text-red-600'" 
             class="text-2xl"></i>
          <div>
            <p :class="verificationResult.isValid ? 'text-green-800' : 'text-red-800'" class="font-semibold">
              {{ verificationResult.isValid ? 'All signatures are valid!' : 'Invalid signatures detected!' }}
            </p>
            <p class="text-sm text-gray-600 mt-1">
              Verified at {{ formatDate(verificationResult.verifiedAt) }}
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Pipeline Status -->
    <div v-if="pipelineStatus" class="card">
      <h2 class="text-xl font-semibold text-gray-900 mb-4">Approval Pipeline</h2>
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <span>Status:</span>
          <span 
            class="status-badge"
            :class="getStatusClass(pipelineStatus.status)"
          >
            {{ pipelineStatus.status }}
          </span>
        </div>
        <div v-if="pipelineStatus.currentStep" class="text-sm text-gray-600">
          Current Step: {{ pipelineStatus.currentStep }}
        </div>
        <div class="text-sm text-gray-600">
          Completed Steps: {{ pipelineStatus.completedSteps.join(', ') || 'None' }}
        </div>
      </div>
    </div>

    <!-- Sign Modal -->
    <div v-if="showSignModal" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div class="card w-full max-w-md m-4">
        <h3 class="text-xl font-semibold text-gray-900 mb-4">Sign Article</h3>
        
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">Reason (optional)</label>
            <input 
              v-model="signReason"
              type="text" 
              class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="I approve this article..."
            />
          </div>
          
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">Location (optional)</label>
            <input 
              v-model="signLocation"
              type="text" 
              class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="New York, USA"
            />
          </div>

          <div v-if="error" class="bg-red-50 text-red-600 p-3 rounded-lg text-sm">
            {{ error }}
          </div>

          <div class="flex gap-2 pt-4">
            <button @click="showSignModal = false" class="btn-secondary flex-1">
              Cancel
            </button>
            <button @click="handleSign" :disabled="articlesStore.loading" class="btn-success flex-1">
              <i v-if="articlesStore.loading" class="pi pi-spin pi-spinner mr-2"></i>
              Sign Document
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div v-else class="text-center py-12">
    <i class="pi pi-spin pi-spinner text-4xl text-gray-400"></i>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useArticlesStore } from '@/stores/articles'
import type { Signature, VerificationResult, PipelineStatus } from '@/types'

const route = useRoute()
const articlesStore = useArticlesStore()

const showSignModal = ref(false)
const signReason = ref('')
const signLocation = ref('')
const error = ref('')

const article = computed(() => articlesStore.currentArticle)
const signatures = computed(() => articlesStore.signatures)
const verificationResult = computed(() => articlesStore.verificationResult)
const pipelineStatus = computed(() => articlesStore.pipelineStatus)

const getStatusClass = (status: string) => {
  const map: Record<string, string> = {
    'IDLE': 'status-pending',
    'RUNNING': 'status-pending',
    'COMPLETED': 'status-approved',
    'FAILED': 'status-rejected'
  }
  return map[status] || 'status-pending'
}

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

async function downloadDocument() {
  if (article.value) {
    await articlesStore.downloadDocument(article.value.id)
  }
}

async function verifySignatures() {
  if (article.value) {
    await articlesStore.verifyArticle(article.value.id)
  }
}

async function handleSign() {
  error.value = ''
  if (article.value) {
    const result = await articlesStore.signArticle(
      article.value.id,
      signReason.value || undefined,
      signLocation.value || undefined
    )
    
    if (result.success) {
      showSignModal.value = false
      signReason.value = ''
      signLocation.value = ''
      await articlesStore.fetchSignatures(article.value.id)
    } else {
      error.value = result.error || 'Failed to sign'
    }
  }
}

onMounted(async () => {
  const articleId = parseInt(route.params.id as string)
  await articlesStore.fetchById(articleId)
  await articlesStore.fetchSignatures(articleId)
  await articlesStore.getPipelineStatus(articleId)
})
</script>
