<template>
  <div class="space-y-8">
    <!-- Welcome Header -->
    <div class="bg-gradient-to-r from-blue-600 to-indigo-700 rounded-xl p-8 text-white shadow-lg">
      <h1 class="text-3xl font-bold mb-2">Welcome to ScienceHub</h1>
      <p class="text-blue-100">Secure E-Signature Platform for Scientific Articles</p>
    </div>

    <!-- Stats Grid -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <div class="card">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-500 mb-1">Total Articles</p>
            <p class="text-3xl font-bold text-gray-900">{{ articlesStore.articles.length }}</p>
          </div>
          <div class="bg-blue-100 p-3 rounded-full">
            <i class="pi pi-book text-blue-600 text-2xl"></i>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-500 mb-1">Signed Articles</p>
            <p class="text-3xl font-bold text-green-600">{{ articlesStore.signedArticles.length }}</p>
          </div>
          <div class="bg-green-100 p-3 rounded-full">
            <i class="pi pi-check-circle text-green-600 text-2xl"></i>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-500 mb-1">Pending Review</p>
            <p class="text-3xl font-bold text-yellow-600">{{ articlesStore.pendingArticles.length }}</p>
          </div>
          <div class="bg-yellow-100 p-3 rounded-full">
            <i class="pi pi-clock text-yellow-600 text-2xl"></i>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-500 mb-1">My Signatures</p>
            <p class="text-3xl font-bold text-purple-600">{{ signatureCount }}</p>
          </div>
          <div class="bg-purple-100 p-3 rounded-full">
            <i class="pi pi-signature text-purple-600 text-2xl"></i>
          </div>
        </div>
      </div>
    </div>

    <!-- Quick Actions -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <router-link to="/articles/create" class="card hover:shadow-lg transition-shadow cursor-pointer border-2 border-transparent hover:border-blue-200">
        <div class="text-center py-6">
          <i class="pi pi-plus-circle text-4xl text-blue-600 mb-4"></i>
          <h3 class="text-lg font-semibold text-gray-900 mb-2">Create Article</h3>
          <p class="text-gray-500 text-sm">Submit a new scientific article for review</p>
        </div>
      </router-link>

      <router-link to="/signatures" class="card hover:shadow-lg transition-shadow cursor-pointer border-2 border-transparent hover:border-green-200">
        <div class="text-center py-6">
          <i class="pi pi-signature text-4xl text-green-600 mb-4"></i>
          <h3 class="text-lg font-semibold text-gray-900 mb-2">Sign Documents</h3>
          <p class="text-gray-500 text-sm">Review and sign pending documents</p>
        </div>
      </router-link>

      <router-link to="/certificates" class="card hover:shadow-lg transition-shadow cursor-pointer border-2 border-transparent hover:border-purple-200">
        <div class="text-center py-6">
          <i class="pi pi-id-card text-4xl text-purple-600 mb-4"></i>
          <h3 class="text-lg font-semibold text-gray-900 mb-2">My Certificate</h3>
          <p class="text-gray-500 text-sm">Manage your digital signing certificate</p>
        </div>
      </router-link>
    </div>

    <!-- Recent Articles -->
    <div class="card">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">Recent Articles</h2>
        <router-link to="/articles" class="text-blue-600 hover:text-blue-700 text-sm font-medium">
          View All →
        </router-link>
      </div>

      <div v-if="articlesStore.loading" class="text-center py-8">
        <i class="pi pi-spin pi-spinner text-4xl text-gray-400"></i>
      </div>

      <div v-else-if="articlesStore.articles.length === 0" class="text-center py-8 text-gray-500">
        <i class="pi pi-inbox text-4xl mb-3"></i>
        <p>No articles yet. Create your first article!</p>
      </div>

      <div v-else class="space-y-4">
        <div 
          v-for="article in recentArticles" 
          :key="article.id"
          class="flex items-center justify-between p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors cursor-pointer"
          @click="$router.push(`/articles/${article.id}`)"
        >
          <div class="flex-1">
            <h3 class="font-semibold text-gray-900">{{ article.title }}</h3>
            <p class="text-sm text-gray-500 mt-1">By {{ article.authorName }} • {{ formatDate(article.createdAt) }}</p>
          </div>
          <span 
            class="status-badge"
            :class="getStatusClass(article.status)"
          >
            {{ article.status }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useArticlesStore } from '@/stores/articles'
import type { Article } from '@/types'

const articlesStore = useArticlesStore()

const recentArticles = computed(() => 
  articlesStore.articles.slice(0, 5)
)

const signatureCount = computed(() => {
  // This would come from a signatures store in production
  return articlesStore.signedArticles.length
})

const getStatusClass = (status: string) => {
  const map: Record<string, string> = {
    'DRAFT': 'status-pending',
    'PENDING_REVIEW': 'status-pending',
    'APPROVED': 'status-approved',
    'REJECTED': 'status-rejected',
    'SIGNED': 'status-signed'
  }
  return map[status] || 'status-pending'
}

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  })
}

onMounted(async () => {
  await articlesStore.fetchAll()
})
</script>
