<template>
  <div class="min-h-[80vh] flex items-center justify-center">
    <div class="card w-full max-w-md">
      <div class="text-center mb-8">
        <i class="pi pi-user-plus text-5xl text-blue-600 mb-4"></i>
        <h1 class="text-2xl font-bold text-gray-900">Create Account</h1>
        <p class="text-gray-500 mt-2">Join ScienceHub today</p>
      </div>

      <form @submit.prevent="handleRegister" class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Full Name</label>
          <input 
            v-model="name"
            type="text" 
            required
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="John Doe"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Email</label>
          <input 
            v-model="email"
            type="email" 
            required
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="you@example.com"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Password</label>
          <input 
            v-model="password"
            type="password" 
            required
            minlength="6"
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="••••••••"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">Role</label>
          <select 
            v-model="role"
            class="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="AUTHOR">Author</option>
            <option value="REVIEWER">Reviewer</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>

        <div v-if="authStore.error" class="bg-red-50 text-red-600 p-3 rounded-lg text-sm">
          {{ authStore.error }}
        </div>

        <button 
          type="submit" 
          :disabled="authStore.loading"
          class="w-full btn-primary flex items-center justify-center"
        >
          <i v-if="authStore.loading" class="pi pi-spin pi-spinner mr-2"></i>
          Create Account
        </button>
      </form>

      <p class="text-center mt-6 text-gray-500">
        Already have an account? 
        <router-link to="/login" class="text-blue-600 hover:underline">Sign In</router-link>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { User } from '@/types'

const router = useRouter()
const authStore = useAuthStore()

const name = ref('')
const email = ref('')
const password = ref('')
const role = ref<User['role']>('AUTHOR')

async function handleRegister() {
  const result = await authStore.register({
    name: name.value,
    email: email.value,
    password: password.value,
    role: role.value
  })
  
  if (result.success) {
    router.push('/')
  }
}
</script>
