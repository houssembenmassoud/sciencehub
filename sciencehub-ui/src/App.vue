<template>
  <div id="app" class="min-h-screen bg-gray-50">
    <nav class="bg-white shadow-lg border-b border-gray-200">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex items-center">
            <router-link to="/" class="flex items-center space-x-3">
              <i class="pi pi-book text-blue-600 text-2xl"></i>
              <span class="text-xl font-bold text-gray-900">ScienceHub</span>
            </router-link>
            <div class="hidden md:flex ml-10 space-x-4">
              <router-link 
                v-for="item in navItems" 
                :key="item.path"
                :to="item.path"
                class="px-3 py-2 rounded-md text-sm font-medium transition-colors"
                :class="isActive(item.path) ? 'bg-blue-100 text-blue-700' : 'text-gray-700 hover:bg-gray-100'"
              >
                {{ item.name }}
              </router-link>
            </div>
          </div>
          <div class="flex items-center space-x-4">
            <button 
              v-if="authStore.isAuthenticated"
              @click="logout"
              class="px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-50 rounded-md transition-colors"
            >
              <i class="pi pi-sign-out mr-2"></i>Logout
            </button>
            <router-link 
              v-else
              to="/login"
              class="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-md transition-colors"
            >
              <i class="pi pi-sign-in mr-2"></i>Login
            </router-link>
          </div>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <router-view />
    </main>

    <footer class="bg-white border-t border-gray-200 mt-12">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <p class="text-center text-gray-500 text-sm">
          © 2024 ScienceHub. Secure E-Signature Platform for Scientific Articles.
        </p>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const navItems = [
  { name: 'Dashboard', path: '/' },
  { name: 'Articles', path: '/articles' },
  { name: 'Signatures', path: '/signatures' },
  { name: 'Certificates', path: '/certificates' }
]

const isActive = (path: string) => {
  return route.path === path || route.path.startsWith(path + '/')
}

const logout = () => {
  authStore.logout()
  router.push('/login')
}
</script>
