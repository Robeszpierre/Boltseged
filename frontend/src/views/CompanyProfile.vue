<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError } from '../api'
const form = ref<any>({}), message = ref(''), error = ref(''), loading = ref(true), saving = ref(false)
async function load() { try { form.value = await api('/api/account/profile') } catch (e) { error.value = e instanceof ApiError ? e.message : 'Az adatok betöltése nem sikerült.' } finally { loading.value = false } }
async function save() { saving.value = true; message.value = ''; error.value = ''; try { form.value = await api('/api/account/profile', { method: 'PUT', body: JSON.stringify({ companyName: form.value.companyName || null, taxNumber: form.value.taxNumber || null, billingAddress: form.value.billingAddress || null, contactName: form.value.contactName || null }) }); message.value = 'Cégadatok mentve.' } catch (e) { error.value = e instanceof ApiError ? e.message : 'A mentés nem sikerült.' } finally { saving.value = false } }
onMounted(load)
</script>
<template><section class="card narrow"><p class="eyebrow">Fiókbeállítások</p><h1>Cégadatok</h1><p v-if="loading" class="muted">Betöltés…</p><form v-else @submit.prevent="save"><label>E-mail<input :value="form.email" readonly></label><label>Cégnév<input v-model="form.companyName"></label><label>Adószám<input v-model="form.taxNumber"></label><label>Számlázási cím<input v-model="form.billingAddress"></label><label>Kapcsolattartó neve<input v-model="form.contactName"></label><p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success-text">{{ message }}</p><button :disabled="saving">{{ saving ? 'Mentés…' : 'Mentés' }}</button></form></section></template>
