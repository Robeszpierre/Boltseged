<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ApiError } from '../api'
import { dateTime } from '../customer'

type Shipment={id:string;status:string;createdAt:string;masterTrackingNumber?:string;recipient:any;packageCount:number;packages:{weight:number}[]}
type Profile={pickupCompanyName?:string;pickupContactName?:string;pickupCountryCode?:string;pickupPostalCode?:string;pickupCityName?:string;pickupAddressLine1?:string;pickupAddressLine2?:string;pickupStateOrProvinceCode?:string;pickupPhone?:string;pickupEmail?:string}

const shipments=ref<Shipment[]>([]),bookings=ref<any[]>([]),selected=ref<string[]>([]),profile=ref<Profile>({}),loading=ref(true),saving=ref(false),error=ref(''),success=ref<any>(),copied=ref('')
let initialShipmentSelectionApplied=false
const localDate=(d:Date)=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
const nextWorkday=()=>{const d=new Date();d.setDate(d.getDate()+1);while([0,6].includes(d.getDay()))d.setDate(d.getDate()+1);return d}
const firstDate=nextWorkday(),pickupDate=ref(localDate(firstDate)),maxDate=localDate(new Date(Date.now()+10*86400000)),readyHour=ref('13'),readyMinute=ref('00'),closeHour=ref('17'),closeMinute=ref('00'),location=ref(''),locationType=ref('business'),instructions=ref('')
const hourOptions=Array.from({length:24},(_,i)=>String(i).padStart(2,'0')),minuteOptions=['00','15','30','45']
const readyTime=computed(()=>`${readyHour.value}:${readyMinute.value}`),closeTime=computed(()=>`${closeHour.value}:${closeMinute.value}`)
const available=computed(()=>shipments.value.filter(s=>s.status==='CREATED'))
const selectedShipments=computed(()=>available.value.filter(s=>selected.value.includes(s.id)))
const allEligibleSelected=computed(()=>available.value.length>0&&available.value.every(s=>selected.value.includes(s.id)))
const partiallyEligibleSelected=computed(()=>selectedShipments.value.length>0&&!allEligibleSelected.value)
const packageCount=computed(()=>selectedShipments.value.reduce((n,s)=>n+s.packageCount,0))
const totalWeight=computed(()=>selectedShipments.value.flatMap(s=>s.packages).reduce((n,p)=>n+Number(p.weight||0),0))
const pickupComplete=computed(()=>Boolean(profile.value.pickupCompanyName&&profile.value.pickupContactName&&/^[A-Z]{2}$/.test(profile.value.pickupCountryCode||'')&&profile.value.pickupPostalCode&&profile.value.pickupCityName&&profile.value.pickupAddressLine1&&profile.value.pickupPhone))
const pickupLine=computed(()=>[profile.value.pickupPostalCode,profile.value.pickupCityName,profile.value.pickupAddressLine1,profile.value.pickupAddressLine2].filter(Boolean).join(', '))
const recipient=(s:Shipment)=>s.recipient?.companyName||s.recipient?.contactName||'—'

function toggleAllEligible(){selected.value=allEligibleSelected.value?[]:available.value.map(s=>s.id)}
async function load(){loading.value=true;error.value='';try{const [loadedShipments,loadedBookings,loadedProfile]=await Promise.all([api('/api/shipments/pickup-eligible'),api('/api/pickups'),api('/api/account/profile')]) as [Shipment[],any[],Profile];shipments.value=loadedShipments;bookings.value=loadedBookings;profile.value=loadedProfile;if(!initialShipmentSelectionApplied){selected.value=loadedShipments.map(s=>s.id);initialShipmentSelectionApplied=true}else{const eligible=new Set(loadedShipments.map(s=>s.id));selected.value=selected.value.filter(id=>eligible.has(id))}}catch(e){error.value=e instanceof ApiError?e.message:'Az adatok betöltése nem sikerült.'}finally{loading.value=false}}
async function create(){error.value='';success.value=undefined;if(!pickupComplete.value){error.value='A futár rendeléséhez előbb töltsd ki a Feladói adatok menüpontban a felvételi címet.';return}if(!selected.value.length){error.value='Válassz legalább egy küldeményt.';return}if(readyTime.value>=closeTime.value){error.value='Az átvétel kezdete legyen korábbi a zárási időnél.';return}saving.value=true;try{success.value=await api('/api/pickups',{method:'POST',body:JSON.stringify({shipmentIds:selected.value,pickupDate:pickupDate.value,readyTime:readyTime.value,closeTime:closeTime.value,locationType:locationType.value,location:location.value||null,specialInstructions:instructions.value||null})});await load()}catch(e){error.value=e instanceof ApiError?e.message:'A futár megrendelése nem sikerült.'}finally{saving.value=false}}
async function cancel(booking:any){if(!confirm('Biztosan lemondod ezt a futárrendelést?'))return;error.value='';try{await api(`/api/pickups/${booking.id}`,{method:'DELETE'});await load()}catch(e){error.value=e instanceof ApiError?e.message:'A futárrendelés lemondása nem sikerült.'}}
async function copy(value:string){await navigator.clipboard?.writeText(value);copied.value=value;setTimeout(()=>copied.value='',1500)}
onMounted(load)
</script>

<template>
  <div class="hero"><div><p class="eyebrow">DHL Express</p><h1>Futár rendelése</h1><p class="muted">Már létrehozott küldeményeidhez rendelj futárt.</p></div></div>
  <p class="muted">* Kötelező mező</p>
  <p v-if="error" class="alert error">{{error}}</p>
  <section v-if="success" class="card success"><h2>Futár sikeresen megrendelve</h2><p><b>Felvétel:</b> {{success.pickupDate}} {{success.readyTime}}–{{success.closeTime}}</p><p><b>Cím:</b> {{success.address.postalCode}} {{success.address.cityName}}, {{success.address.addressLine1}}</p><p><b>Csomagok:</b> {{success.packageCount}} db · {{success.totalWeight}} kg</p><p v-for="number in success.dispatchConfirmationNumbers" :key="number"><b>DHL visszaigazolás:</b> {{number}} <button class="copy" @click="copy(number)">{{copied===number?'Másolva':'Másolás'}}</button></p><p v-for="warning in success.warnings" :key="warning" class="alert">{{warning}}</p></section>
  <section class="card"><h2>1. Küldemények kiválasztása</h2><div v-if="loading" class="empty">Betöltés…</div><div v-else-if="!available.length" class="empty">Nincs felvehető, létrehozott küldeményed.</div><template v-else><label class="checkbox"><input :checked="allEligibleSelected" :indeterminate="partiallyEligibleSelected" type="checkbox" @change="toggleAllEligible">Összes küldemény kiválasztása</label><label v-for="s in available" :key="s.id" class="pickup-shipment"><input v-model="selected" type="checkbox" :value="s.id"><span><b>{{s.masterTrackingNumber||'Tracking nélkül'}}</b><br>{{dateTime(s.createdAt)}} · {{recipient(s)}} · {{s.packageCount}} csomag · {{s.packages.reduce((n,p)=>n+Number(p.weight),0)}} kg</span></label><p v-if="selected.length"><b>Összesen:</b> {{packageCount}} csomag · {{totalWeight}} kg</p></template></section>
  <form @submit.prevent="create">
    <section class="card"><h2>2. Felvételi idő</h2><div class="address-grid"><label>Felvétel dátuma *<input v-model="pickupDate" required type="date" :min="localDate(firstDate)" :max="maxDate"></label><label>Átvehető ettől *<span class="time-select"><select v-model="readyHour" aria-label="Kezdő óra"><option v-for="hour in hourOptions" :key="hour" :value="hour">{{hour}}</option></select> : <select v-model="readyMinute" aria-label="Kezdő perc"><option v-for="minute in minuteOptions" :key="minute" :value="minute">{{minute}}</option></select></span></label><label>Átvétel legkésőbb *<span class="time-select"><select v-model="closeHour" aria-label="Záró óra"><option v-for="hour in hourOptions" :key="hour" :value="hour">{{hour}}</option></select> : <select v-model="closeMinute" aria-label="Záró perc"><option v-for="minute in minuteOptions" :key="minute" :value="minute">{{minute}}</option></select></span></label></div></section>
    <section class="card"><div class="section-head"><div><h2>3. Felvételi cím</h2><p class="muted">A mentett felvételi címet használjuk a futárrendeléshez.</p></div><RouterLink class="button secondary" to="/company-profile">Feladói adatok szerkesztése</RouterLink></div><p v-if="!loading&&!pickupComplete" class="alert error">A futár rendeléséhez előbb töltsd ki a Feladói adatok menüpontban a felvételi címet.</p><div v-else-if="pickupComplete"><p><b>{{profile.pickupCompanyName}}</b> · {{profile.pickupContactName}}</p><p>{{profile.pickupCountryCode}} · {{pickupLine}}</p><p>{{profile.pickupPhone}}<span v-if="profile.pickupEmail"> · {{profile.pickupEmail}}</span></p></div><div class="address-grid"><label>Átvétel helye<input v-model.trim="location" maxlength="100" placeholder="pl. recepció"></label><label>Cím típusa *<select v-model="locationType"><option value="business">Üzleti cím</option><option value="residence">Lakóhely</option></select></label><label class="wide">Futárnak szóló megjegyzés<textarea v-model.trim="instructions" maxlength="250"></textarea></label></div></section>
    <button :disabled="saving||loading||!pickupComplete">{{saving?'Futár rendelése…':'Futár rendelése'}}</button>
  </form>
  <section class="card pickup-history"><h2>Korábbi futárrendelések</h2><div v-if="!bookings.length" class="empty">Még nincs futárrendelésed.</div><article v-for="b in bookings" :key="b.id" class="document"><div><b>{{b.pickupDate}} · {{b.address.cityName}}, {{b.address.addressLine1}}</b><br>{{b.packageCount}} csomag · {{b.dispatchConfirmationNumbers.join(', ')}} · {{b.status==='BOOKED'?'Lefoglalva':'Lemondva'}}</div><button v-if="b.status==='BOOKED'" class="secondary" @click="cancel(b)">Futárrendelés lemondása</button></article></section>
</template>

<style scoped>
.time-select{display:flex;align-items:center;gap:.45rem;margin-top:.35rem}.time-select select{min-width:5rem}
</style>
