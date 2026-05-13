<template>
  <div class="max-w-3xl mx-auto">
    <button @click="$router.back()" class="text-gray-500 hover:text-gray-700 mb-4">
      <i class="pi pi-arrow-left mr-2"></i>Back
    </button>

    <div class="card">
      <h1 class="text-2xl font-bold text-gray-900 mb-6">Create New Article</h1>

      <form @submit.prevent="handleSubmit" class="space-y-6">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Title *</label>
          <input 
            v-model="title"
            type="text" 
            required
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="Enter article title"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Abstract *</label>
          <textarea 
            v-model="abstract"
            rows="4"
            required
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="Brief summary of your research..."
          ></textarea>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Content *</label>
          <textarea 
            v-model="content"
            rows="12"
            required
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="Full article content..."
          ></textarea>
        </div>

        <div v-if="error" class="bg-red-50 text-red-600 p-3 rounded-lg text-sm">
          {{ error }}
        </div>

        <div class="flex gap-2 pt-4">
          <button type="button" @click="$router.back()" class="btn-secondary flex-1">
            Cancel
          </button>
          <button 
            type="submit" 
            :disabled="articlesStore.loading"
            class="btn-primary flex-1"
          >
            <i v-if="articlesStore.loading" class="pi pi-spin pi-spinner mr-2"></i>
            Create Article
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useArticlesStore } from '@/stores/articles'

const router = useRouter()
const articlesStore = useArticlesStore()

const title = ref('')
const abstract = ref('')
const content = ref('')
const error = ref('')

async function handleSubmit() {
  error.value = ''
  
  const result = await articlesStore.create({
    title: title.value,
    abstract: abstract.value,
    content: content.value,
    status: 'DRAFT'
  })

  if (result.success) {
    router.push(`/articles/${result.article?.id}`)
  } else {
    error.value = result.error || 'Failed to create article'
  }
}
</script>
