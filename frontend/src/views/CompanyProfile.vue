<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError } from '../api'

const form = ref<any>({})
const message = ref('')
const error = ref('')
const loading = ref(true)
const saving = ref(false)

const nullable = (value: unknown) => typeof value === 'string' && value.trim() ? value.trim() : null
const country = (value: unknown) => typeof value === 'string' && value.trim() ? value.trim().toUpperCase() : null

async function load() {
  try {
    form.value = await api('/api/account/profile')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Az adatok betöltése nem sikerült.'
  } finally {
    loading.value = false
  }
}

function copyCompanyAddress() {
  form.value.pickupCompanyName = form.value.companyName || ''
  form.value.pickupContactName = form.value.contactName || ''
  form.value.pickupCountryCode = form.value.senderCountryCode || ''
  form.value.pickupPostalCode = form.value.senderPostalCode || ''
  form.value.pickupCityName = form.value.senderCityName || ''
  form.value.pickupAddressLine1 = form.value.senderAddressLine1 || ''
  form.value.pickupAddressLine2 = form.value.senderAddressLine2 || ''
  form.value.pickupStateOrProvinceCode = form.value.senderStateOrProvinceCode || ''
  form.value.pickupPhone = form.value.senderPhone || ''
  form.value.pickupEmail = form.value.email || ''
}

async function save() {
  saving.value = true
  message.value = ''
  error.value = ''
  try {
    const body = {
      companyName: nullable(form.value.companyName),
      taxNumber: nullable(form.value.taxNumber),
      billingAddress: nullable(form.value.billingAddress),
      contactName: nullable(form.value.contactName),
      senderCountryCode: country(form.value.senderCountryCode),
      senderPostalCode: nullable(form.value.senderPostalCode),
      senderCityName: nullable(form.value.senderCityName),
      senderAddressLine1: nullable(form.value.senderAddressLine1),
      senderAddressLine2: nullable(form.value.senderAddressLine2),
      senderStateOrProvinceCode: nullable(form.value.senderStateOrProvinceCode),
      senderPhone: nullable(form.value.senderPhone),
      pickupCompanyName: nullable(form.value.pickupCompanyName),
      pickupContactName: nullable(form.value.pickupContactName),
      pickupCountryCode: country(form.value.pickupCountryCode),
      pickupPostalCode: nullable(form.value.pickupPostalCode),
      pickupCityName: nullable(form.value.pickupCityName),
      pickupAddressLine1: nullable(form.value.pickupAddressLine1),
      pickupAddressLine2: nullable(form.value.pickupAddressLine2),
      pickupStateOrProvinceCode: nullable(form.value.pickupStateOrProvinceCode),
      pickupPhone: nullable(form.value.pickupPhone),
      pickupEmail: nullable(form.value.pickupEmail),
    }
    form.value = await api('/api/account/profile', { method: 'PUT', body: JSON.stringify(body) })
    message.value = 'Feladói adatok mentve.'
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'A mentés nem sikerült.'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="card narrow">
    <p class="eyebrow">Fiókbeállítások</p>
    <h1>Feladói adatok</h1>
    <p class="muted">A küldemények feladója és a futár felvételi címe innen töltődik be.</p>
    <p v-if="loading" class="muted">Betöltés…</p>
    <form v-else @submit.prevent="save">
      <h2>Céges adatok</h2>
      <div class="address-grid">
        <label>E-mail<input :value="form.email" readonly /></label>
        <label>Cégnév *<input v-model.trim="form.companyName" /></label>
        <label>Kapcsolattartó neve *<input v-model.trim="form.contactName" /></label>
        <label>Adószám<input v-model.trim="form.taxNumber" /></label>
        <label class="wide">Számlázási cím<textarea v-model.trim="form.billingAddress"></textarea></label>
        <label>Országkód *<input v-model.trim="form.senderCountryCode" maxlength="2" /></label>
        <label>Irányítószám *<input v-model.trim="form.senderPostalCode" /></label>
        <label>Város *<input v-model.trim="form.senderCityName" /></label>
        <label>Cím *<input v-model.trim="form.senderAddressLine1" /></label>
        <label>Cím 2<input v-model.trim="form.senderAddressLine2" /></label>
        <label>Állam / tartomány<input v-model.trim="form.senderStateOrProvinceCode" /></label>
        <label>Telefonszám *<input v-model.trim="form.senderPhone" /></label>
      </div>

      <hr />
      <div class="section-head">
        <div>
          <h2>Felvételi cím</h2>
          <p class="muted">Ezt a címet használjuk a DHL futár megrendelésekor.</p>
        </div>
        <button type="button" class="secondary" @click="copyCompanyAddress">A felvételi cím megegyezik a céges címmel</button>
      </div>
      <div class="address-grid">
        <label>Cégnév *<input v-model.trim="form.pickupCompanyName" /></label>
        <label>Kapcsolattartó neve *<input v-model.trim="form.pickupContactName" /></label>
        <label>Országkód *<input v-model.trim="form.pickupCountryCode" maxlength="2" /></label>
        <label>Irányítószám *<input v-model.trim="form.pickupPostalCode" /></label>
        <label>Város *<input v-model.trim="form.pickupCityName" /></label>
        <label>Cím *<input v-model.trim="form.pickupAddressLine1" /></label>
        <label>Cím 2<input v-model.trim="form.pickupAddressLine2" /></label>
        <label>Állam / tartomány<input v-model.trim="form.pickupStateOrProvinceCode" /></label>
        <label>Telefonszám *<input v-model.trim="form.pickupPhone" /></label>
        <label>E-mail<input v-model.trim="form.pickupEmail" type="email" /></label>
      </div>

      <p v-if="error" class="alert error">{{ error }}</p>
      <p v-if="message" class="alert success-text">{{ message }}</p>
      <button :disabled="saving">{{ saving ? 'Mentés…' : 'Mentés' }}</button>
    </form>
  </section>
</template>
