<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import App from './App.vue'
import EntryPage from './components/EntryPage.vue'
import { api, initializeCsrf } from './lib/api'
import { loadPhotos } from './lib/photos'
import { sessionAccount, type RegistrationInput, type SessionAccount } from './lib/workspace'

const account = ref<SessionAccount | null>(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
function expired() { account.value = null; error.value = '登录已过期，请重新登录。' }
async function restore() {
  loading.value = true
  try { await initializeCsrf(); account.value = sessionAccount(await api<SessionAccount>('/auth/me')) }
  catch { account.value = null }
  finally { loading.value = false }
}
async function authenticate(path: '/auth/login' | '/auth/register', form: { username: string; password: string } | RegistrationInput) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await initializeCsrf()
    account.value = sessionAccount(await api<SessionAccount>(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form) }))
  } catch (e) { error.value = (e as Error).message }
  finally { busy.value = false }
}
function login(username: string, password: string) { return authenticate('/auth/login', { username, password }) }
function register(form: RegistrationInput) { return authenticate('/auth/register', form) }
async function logout() {
  try { await api('/auth/logout', { method: 'POST' }); account.value = null; error.value = '' }
  catch (e) { error.value = (e as Error).message }
}
onMounted(() => { window.addEventListener('session-expired', expired); loadPhotos().catch(() => {}); restore() })
onUnmounted(() => window.removeEventListener('session-expired', expired))
</script>
<template><div v-if="loading" class="entry-loading">正在检查登录状态…</div><App v-else-if="account" :account="account" @exit="logout"/><EntryPage v-else :error="error" :busy="busy" @login="login" @register="register" @mode-change="error=''"/></template>
