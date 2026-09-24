<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { loggedIn, logout, role } from './auth'
import { api } from './api'
const admin = computed(() => role.value === 'ADMIN')
const identity = ref('')
onMounted(async () => { if (loggedIn() && !admin.value) { try { const profile = await api('/api/account/profile'); identity.value = profile.companyName || profile.email } catch {} } })
</script>
<template><header v-if="loggedIn()"><strong>Boltségéd</strong><nav v-if="admin"><RouterLink to="/admin">Dashboard</RouterLink><RouterLink to="/admin/customers">Ügyfelek</RouterLink><RouterLink to="/admin/shipments">Szállítmányok</RouterLink><RouterLink to="/admin/billing">Számlázás</RouterLink><button class="nav-button" @click="logout">Kilépés</button></nav><nav v-else><span v-if="identity" class="identity">{{ identity }}</span><RouterLink to="/dashboard">Áttekintés</RouterLink><RouterLink to="/shipments/new">Új küldemény</RouterLink><RouterLink to="/shipments">Küldeményeim</RouterLink><RouterLink to="/pickup">Futár rendelése</RouterLink><RouterLink to="/company-profile">Cégadatok</RouterLink><RouterLink to="/account">Fiók</RouterLink><button class="nav-button" @click="logout">Kijelentkezés</button></nav></header><main><RouterView/></main></template>
