<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-2xl font-bold text-gray-900">Digital Certificates</h1>
      <p class="text-gray-500 mt-1">Manage your PKI certificates for secure signing</p>
    </div>

    <!-- My Certificate -->
    <div class="card">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-semibold text-gray-900">My Certificate</h2>
        <button @click="regenerateCertificate" :disabled="loading" class="btn-secondary">
          <i v-if="loading" class="pi pi-spin pi-spinner mr-2"></i>
          <i v-else class="pi pi-refresh mr-2"></i>Regenerate
        </button>
      </div>

      <div v-if="loading && !certificate" class="text-center py-8">
        <i class="pi pi-spin pi-spinner text-4xl text-gray-400"></i>
      </div>

      <div v-else-if="!certificate" class="text-center py-8 text-gray-500">
        <i class="pi pi-id-card text-4xl mb-3"></i>
        <p>No certificate found</p>
        <button @click="regenerateCertificate" class="btn-primary mt-4">
          Generate Certificate
        </button>
      </div>

      <div v-else class="certificate-card">
        <div class="flex items-start gap-4">
          <div class="bg-blue-100 p-4 rounded-full">
            <i class="pi pi-id-card text-blue-600 text-3xl"></i>
          </div>
          <div class="flex-1">
            <h3 class="font-semibold text-gray-900 text-lg">{{ certificate.userName }}</h3>
            <div class="grid grid-cols-2 gap-4 mt-4 text-sm">
              <div>
                <span class="text-gray-500">Serial Number:</span>
                <p class="font-mono text-gray-700">{{ formatSerial(certificate.serialNumber) }}</p>
              </div>
              <div>
                <span class="text-gray-500">Issued:</span>
                <p class="text-gray-700">{{ formatDate(certificate.issuedAt) }}</p>
              </div>
              <div>
                <span class="text-gray-500">Expires:</span>
                <p :class="isExpiringSoon ? 'text-red-600 font-medium' : 'text-gray-700'">
                  {{ formatDate(certificate.expiresAt) }}
                </p>
              </div>
              <div>
                <span class="text-gray-500">Algorithm:</span>
                <p class="text-gray-700">RSA 2048-bit</p>
              </div>
            </div>
            <div class="flex gap-2 mt-6">
              <button @click="downloadCertificate" class="btn-primary">
                <i class="pi pi-download mr-2"></i>Download Certificate
              </button>
              <button @click="viewPublicKey" class="btn-secondary">
                <i class="pi pi-eye mr-2"></i>View Public Key
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Certificate Info -->
    <div class="card">
      <h2 class="text-xl font-semibold text-gray-900 mb-4">About Digital Certificates</h2>
      <div class="prose text-gray-600">
        <p>Your digital certificate contains a public/private key pair used for cryptographic signing. When you sign a document:</p>
        <ul class="list-disc pl-5 mt-2 space-y-1">
          <li>A hash of the document is created using SHA-256</li>
          <li>The hash is encrypted with your private key</li>
          <li>Anyone can verify the signature using your public key</li>
          <li>This proves the document hasn't been tampered with</li>
          <li>This proves you were the one who signed it</li>
        </ul>
        <p class="mt-4 text-sm text-gray-500">
          <i class="pi pi-info-circle mr-1"></i>
          Your private key is stored securely and never leaves the server.
        </p>
      </div>
    </div>

    <!-- Public Key Modal -->
    <div v-if="showPublicKeyModal" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div class="card w-full max-w-2xl m-4">
        <h3 class="text-xl font-semibold text-gray-900 mb-4">Public Key</h3>
        <pre class="bg-gray-100 p-4 rounded-lg overflow-x-auto text-sm font-mono text-gray-700 max-h-64">{{ certificate?.publicKey }}</pre>
        <div class="flex justify-end mt-4">
          <button @click="showPublicKeyModal = false" class="btn-secondary">
            Close
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { Certificate } from '@/types'
import { certificateService } from '@/services/api'

const certificate = ref<Certificate | null>(null)
const loading = ref(false)
const showPublicKeyModal = ref(false)

const isExpiringSoon = computed(() => {
  if (!certificate.value) return false
  const expiresAt = new Date(certificate.value.expiresAt)
  const now = new Date()
  const daysUntilExpiry = (expiresAt.getTime() - now.getTime()) / (1000 * 60 * 60 * 24)
  return daysUntilExpiry < 30
})

const formatSerial = (serial: string) => {
  return serial.match(/.{1,2}/g)?.join(':') || serial
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

async function fetchCertificate() {
  loading.value = true
  try {
    const response = await certificateService.getCurrentUserCertificate()
    certificate.value = response.data
  } catch (err) {
    console.error('Failed to fetch certificate:', err)
  } finally {
    loading.value = false
  }
}

async function regenerateCertificate() {
  if (!confirm('Are you sure? This will invalidate your current certificate.')) return
  
  loading.value = true
  try {
    const response = await certificateService.regenerate()
    certificate.value = response.data
  } catch (err) {
    alert('Failed to regenerate certificate')
  } finally {
    loading.value = false
  }
}

async function downloadCertificate() {
  try {
    const response = await certificateService.downloadCurrentUser()
    const blob = new Blob([response.data], { type: 'application/x-x509-ca-cert' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `certificate-${certificate.value?.userName}.pem`
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (err) {
    alert('Failed to download certificate')
  }
}

function viewPublicKey() {
  showPublicKeyModal.value = true
}

onMounted(async () => {
  await fetchCertificate()
})
</script>
