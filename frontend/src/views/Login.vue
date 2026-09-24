<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { setAuth } from '../auth'

const email = ref(''), password = ref(''), confirmation = ref('')
const registering = ref(false), error = ref(''), notice = ref(''), loading = ref(false)
const router = useRouter()
async function submit() {
  error.value = ''; notice.value = ''
  if (registering.value && password.value !== confirmation.value) { error.value = 'A jelszavak nem egyeznek.'; return }
  loading.value = true
  try {
    if (registering.value) {
      await api('/api/auth/register', { method: 'POST', body: JSON.stringify({ email: email.value, password: password.value }) })
      registering.value = false; password.value = ''; confirmation.value = ''
      notice.value = 'Sikeres regisztráció. Most már bejelentkezhetsz.'; return
    }
    const data = await api('/api/auth/login', { method: 'POST', body: JSON.stringify({ email: email.value, password: password.value }) })
    setAuth(data.token, data.role); await router.push('/dashboard')
  } catch (e) { error.value = e instanceof ApiError ? e.message : 'A szolgáltatás jelenleg nem érhető el.' }
  finally { loading.value = false }
}
</script>
<template><section class="card login"><p class="eyebrow">Boltségéd</p><h1>{{ registering ? 'Regisztráció' : 'Belépés' }}</h1><p class="muted">{{ registering ? 'Hozd létre fiókodat néhány másodperc alatt.' : 'Lépj be partneri fiókodba.' }}</p><form @submit.prevent="submit"><label>E-mail<input v-model.trim="email" required type="email" autocomplete="email"></label><label>Jelszó<input v-model="password" required :minlength="registering ? 12 : undefined" type="password" autocomplete="current-password"></label><label v-if="registering">Jelszó megerősítése<input v-model="confirmation" required minlength="12" type="password" autocomplete="new-password"></label><p v-if="error" class="alert error">{{ error }}</p><p v-if="notice" class="alert success-text">{{ notice }}</p><button :disabled="loading">{{ loading ? 'Folyamatban…' : (registering ? 'Regisztráció' : 'Belépés') }}</button><button type="button" class="secondary" :disabled="loading" @click="registering = !registering; error = ''; notice = ''">{{ registering ? 'Van már fiókom' : 'Regisztráció' }}</button></form></section></template>
