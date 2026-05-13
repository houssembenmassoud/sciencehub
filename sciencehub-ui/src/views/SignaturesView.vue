<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-2xl font-bold text-gray-900">Signatures</h1>
      <p class="text-gray-500 mt-1">Manage and verify document signatures</p>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div class="card">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-500 mb-1">Total Signatures</p>
            <p class="text-3xl font-bold text-gray-900">{{ signatures.length }}</p>
          </div>
          <div class="bg-blue-100 p-3 rounded-full">
            <i class="pi pi-signature text-blue-600 text-2xl"></i>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-500 mb-1">Valid Signatures</p>
            <p class="text-3xl font-bold text-green-600">{{ validCount }}</p>
          </div>
          <div class="bg-green-100 p-3 rounded-full">
            <i class="pi pi-check-circle text-green-600 text-2xl"></i>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-500 mb-1">Articles Signed</p>
            <p class="text-3xl font-bold text-purple-600">{{ uniqueArticles }}</p>
          </div>
          <div class="bg-purple-100 p-3 rounded-full">
            <i class="pi pi-book text-purple-600 text-2xl"></i>
          </div>
        </div>
      </div>
    </div>

    <!-- Signatures List -->
    <div class="card">
      <h2 class="text-xl font-semibold text-gray-900 mb-6">All Signatures</h2>

      <div v-if="articlesStore.loading" class="text-center py-8">
        <i class="pi pi-spin pi-spinner text-4xl text-gray-400"></i>
      </div>

      <div v-else-if="signatures.length === 0" class="text-center py-8 text-gray-500">
        <i class="pi pi-signature text-4xl mb-3"></i>
        <p>No signatures found</p>
      </div>

      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="border-b border-gray-200">
              <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">Signer</th>
              <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">Article</th>
              <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">Date</th>
              <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">Reason</th>
              <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">Status</th>
              <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr 
              v-for="sig in signatures" 
              :key="sig.id"
              class="border-b border-gray-100 hover:bg-gray-50"
            >
              <td class="py-3 px-4">
                <div class="flex items-center gap-2">
                  <i class="pi pi-user text-gray-400"></i>
                  <span class="font-medium">{{ sig.signerName }}</span>
                </div>
              </td>
              <td class="py-3 px-4">
                <button 
                  @click="viewArticle(sig.articleId)"
                  class="text-blue-600 hover:underline"
                >
                  Article #{{ sig.articleId }}
                </button>
              </td>
              <td class="py-3 px-4 text-sm text-gray-600">
                {{ formatDate(sig.signedAt) }}
              </td>
              <td class="py-3 px-4 text-sm text-gray-600 max-w-xs truncate">
                {{ sig.reason || '-' }}
              </td>
              <td class="py-3 px-4">
                <span class="status-badge status-approved">
                  <i class="pi pi-check-circle mr-1"></i>Valid
                </span>
              </td>
              <td class="py-3 px-4">
                <button 
                  @click="verifySignature(sig)"
                  class="text-sm text-blue-600 hover:text-blue-700 font-medium"
                >
                  Verify
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useArticlesStore } from '@/stores/articles'
import type { Signature } from '@/types'

const router = useRouter()
const articlesStore = useArticlesStore()

const signatures = computed(() => articlesStore.signatures)
const validCount = computed(() => signatures.value.filter(s => s.isValid !== false).length)
const uniqueArticles = computed(() => 
  new Set(signatures.value.map(s => s.articleId)).size
)

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function viewArticle(articleId: number) {
  router.push(`/articles/${articleId}`)
}

async function verifySignature(signature: Signature) {
  await articlesStore.verifyArticle(signature.articleId)
}

onMounted(async () => {
  await articlesStore.fetchSignatures()
})
</script>
