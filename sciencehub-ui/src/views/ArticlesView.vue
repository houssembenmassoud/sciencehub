<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Articles</h1>
        <p class="text-gray-500 mt-1">Manage and sign scientific articles</p>
      </div>
      <router-link to="/articles/create" class="btn-primary flex items-center">
        <i class="pi pi-plus mr-2"></i>
        New Article
      </router-link>
    </div>

    <!-- Filters -->
    <div class="card">
      <div class="flex flex-wrap gap-4">
        <div class="flex-1 min-w-[200px]">
          <input 
            v-model="searchQuery"
            type="text" 
            placeholder="Search articles..."
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
        <select 
          v-model="statusFilter"
          class="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="">All Statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="PENDING_REVIEW">Pending Review</option>
          <option value="APPROVED">Approved</option>
          <option value="SIGNED">Signed</option>
          <option value="REJECTED">Rejected</option>
        </select>
      </div>
    </div>

    <!-- Articles List -->
    <div v-if="articlesStore.loading" class="text-center py-12">
      <i class="pi pi-spin pi-spinner text-4xl text-gray-400"></i>
    </div>

    <div v-else-if="filteredArticles.length === 0" class="card text-center py-12 text-gray-500">
      <i class="pi pi-inbox text-4xl mb-3"></i>
      <p>No articles found</p>
    </div>

    <div v-else class="grid gap-4">
      <div 
        v-for="article in filteredArticles" 
        :key="article.id"
        class="card hover:shadow-lg transition-shadow cursor-pointer"
        @click="$router.push(`/articles/${article.id}`)"
      >
        <div class="flex items-start justify-between">
          <div class="flex-1">
            <h3 class="text-lg font-semibold text-gray-900">{{ article.title }}</h3>
            <p class="text-gray-600 mt-2 line-clamp-2">{{ article.abstract }}</p>
            <div class="flex items-center gap-4 mt-4 text-sm text-gray-500">
              <span><i class="pi pi-user mr-1"></i>{{ article.authorName }}</span>
              <span><i class="pi pi-calendar mr-1"></i>{{ formatDate(article.createdAt) }}</span>
            </div>
          </div>
          <div class="flex flex-col items-end gap-2">
            <span 
              class="status-badge"
              :class="getStatusClass(article.status)"
            >
              {{ article.status.replace('_', ' ') }}
            </span>
            <button 
              class="text-blue-600 hover:text-blue-700 text-sm font-medium"
              @click.stop="$router.push(`/articles/${article.id}`)"
            >
              View Details →
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useArticlesStore } from '@/stores/articles'

const articlesStore = useArticlesStore()
const searchQuery = ref('')
const statusFilter = ref('')

const filteredArticles = computed(() => {
  return articlesStore.articles.filter(article => {
    const matchesSearch = article.title.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
                         article.abstract?.toLowerCase().includes(searchQuery.value.toLowerCase())
    const matchesStatus = !statusFilter.value || article.status === statusFilter.value
    return matchesSearch && matchesStatus
  })
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
