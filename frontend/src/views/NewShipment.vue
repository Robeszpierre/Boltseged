<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { api, apiBlob, ApiError } from "../api";
import { money, dateTime, productLabel, statusLabel } from "../customer";
import "./new-shipment.css";
type A = {
  companyName: string;
  contactName: string;
  countryCode: string;
  postalCode: string;
  cityName: string;
  addressLine1: string;
  addressLine2: string;
  stateOrProvinceCode: string;
  phone: string;
  email: string;
};
type P = {
  weight: number | null;
  length: number | null;
  width: number | null;
  height: number | null;
};
type I = {
  number: number;
  description: string;
  quantity: number | null;
  quantityUnitOfMeasurement: string;
  price: number | null;
  manufacturerCountry: string;
  commodityCode: string;
  netWeight: number | null;
  grossWeight: number | null;
  exportReasonType: string;
};
type D = {
  file: File;
  typeCode: string;
  imageFormat: string;
  content?: string;
};
type Q = {
  productCode: string;
  productName?: string;
  estimatedCost: number;
  currency: string;
  estimatedDelivery?: string;
};
const ISO2 = new Set(
  "AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW".split(
    " ",
  ),
);
const EU_COUNTRIES = new Set(
  "AT BE BG HR CY CZ DK EE FI FR DE GR HU IE IT LV LT LU MT NL PL PT RO SK SI ES SE".split(
    " ",
  ),
);
const reasons = [
  ["COMMERCIAL_PURPOSE_OR_SALE", "Kereskedelmi cél / értékesítés"],
  ["PERMANENT", "Végleges export"],
  ["TEMPORARY", "Ideiglenes export"],
  ["RETURN", "Visszaküldés"],
  ["USED_EXHIBITION_GOODS_TO_ORIGIN", "Kiállítási áru visszaküldése"],
  ["INTERCOMPANY_USE", "Cégen belüli használat"],
  [
    "PERSONAL_BELONGINGS_OR_PERSONAL_USE",
    "Személyes tárgy / személyes használat",
  ],
  ["SAMPLE", "Minta"],
  ["GIFT", "Ajándék"],
  ["RETURN_TO_ORIGIN", "Vissza a feladási helyre"],
  ["WARRANTY_REPLACEMENT", "Garanciális csere"],
  ["DIPLOMATIC_GOODS", "Diplomáciai áru"],
  ["DEFENCE_MATERIAL", "Védelmi anyag"],
];
const docTypes = [
  ["CIN", "Commercial Invoice"],
  ["INV", "Invoice / számla"],
  ["PNV", "Proforma Invoice"],
  ["COO", "Származási bizonyítvány"],
  ["NAF", "NAFTA származási bizonyítvány"],
  ["DCL", "Vámnyilatkozat"],
  ["AWB", "Air Waybill / fuvarlevél"],
];
const blankA = (): A => ({
    companyName: "",
    contactName: "",
    countryCode: "",
    postalCode: "",
    cityName: "",
    addressLine1: "",
    addressLine2: "",
    stateOrProvinceCode: "",
    phone: "",
    email: "",
  }),
  blankP = (): P => ({ weight: null, length: null, width: null, height: null }),
  blankI = (number: number): I => ({
    number,
    description: "",
    quantity: null,
    quantityUnitOfMeasurement: "PCS",
    price: null,
    manufacturerCountry: "",
    commodityCode: "",
    netWeight: null,
    grossWeight: null,
    exportReasonType: "",
  }),
  date = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`,
  next = () => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    while ([0, 6].includes(d.getDay())) d.setDate(d.getDate() + 1);
    return date(d);
  };
const DEV_RECIPIENT: A = {
  companyName: "Test Receiver",
  contactName: "Test Receiver",
  countryCode: "US",
  postalCode: "27612",
  cityName: "Raleigh",
  addressLine1: "5711 Three Oaks Drive",
  addressLine2: "",
  stateOrProvinceCode: "NC",
  phone: "+1543453453",
  email: "bbb@bbb.com",
};
const DEV_PACKAGE: P = { weight: 1, length: 20, width: 20, height: 20 };
const DEV_CUSTOMS_ITEM: I = {
  number: 1,
  description: "Wooden gift",
  quantity: 1,
  quantityUnitOfMeasurement: "PCS",
  price: 50,
  manufacturerCountry: "HU",
  commodityCode: "442019",
  netWeight: 0.8,
  grossWeight: 1,
  exportReasonType: "COMMERCIAL_PURPOSE_OR_SALE",
};
const normalizeCountryCode = (value: string) => value.trim().toUpperCase();
const isDevelopment =
  (import.meta as ImportMeta & { env?: { DEV?: boolean } }).env?.DEV === true;
function shouldBeCustomsDeclarable(
  senderCountry: string,
  recipientCountry: string,
) {
  const from = normalizeCountryCode(senderCountry);
  const to = normalizeCountryCode(recipientCountry);

  if (!from || !to || from === to) return false;
  return !(EU_COUNTRIES.has(from) && EU_COUNTRIES.has(to));
}
const min = next(),
  pickupDate = ref(min),
  invoiceDate = ref(min),
  sender = ref(blankA()),
  recipient = ref(isDevelopment ? { ...DEV_RECIPIENT } : blankA()),
  packages = ref<P[]>([isDevelopment ? { ...DEV_PACKAGE } : blankP()]),
  customsOverride = ref<boolean | null>(null),
  items = ref<I[]>([isDevelopment ? { ...DEV_CUSTOMS_ITEM } : blankI(1)]),
  currency = ref(isDevelopment ? "USD" : ""),
  incoterm = ref("DAP"),
  description = ref(""),
  documents = ref<D[]>([]),
  profile = ref<any>({}),
  loadingProfile = ref(true),
  rates = ref<Q[]>([]),
  selected = ref<Q>(),
  quoteLoading = ref(false),
  creating = ref(false),
  error = ref(""),
  validation = ref(""),
  stale = ref(false),
  created = ref<any>(),
  downloading = ref(""),
  copied = ref("");
let key = crypto.randomUUID?.() || `${Date.now()}-${Math.random()}`;
const effectiveCustomsDeclarable = computed(
  () =>
    customsOverride.value ??
    shouldBeCustomsDeclarable(
      sender.value.countryCode,
      recipient.value.countryCode,
    ),
);
const senderProfileComplete = computed(
  () =>
    Boolean(sender.value.companyName) &&
    Boolean(sender.value.contactName) &&
    validCountry(sender.value.countryCode) &&
    Boolean(sender.value.postalCode) &&
    Boolean(sender.value.cityName) &&
    Boolean(sender.value.addressLine1) &&
    Boolean(sender.value.phone) &&
    /^\S+@\S+\.\S+$/.test(sender.value.email),
);
watch(
  [() => sender.value.countryCode, () => recipient.value.countryCode],
  () => {
    customsOverride.value = null;
  },
);
watch(
  [
    sender,
    recipient,
    packages,
    pickupDate,
    effectiveCustomsDeclarable,
    items,
    currency,
    incoterm,
    description,
    documents,
  ],
  () => {
    if (rates.value.length) {
      rates.value = [];
      selected.value = undefined;
      stale.value = true;
    }
  },
  { deep: true },
);
const total = computed(() =>
    packages.value.reduce((n, p) => n + (Number(p.weight) || 0), 0),
  ),
  declared = computed(() =>
    items.value.reduce(
      (n, i) => n + (Number(i.price) || 0) * (Number(i.quantity) || 0),
      0,
    ),
  );
const country = (v: string) => v.trim().toUpperCase();
const validCountry = (v: string) => ISO2.has(country(v));
function valid() {
  let m = "";
  if (!senderProfileComplete.value) {
    m = "A küldemény létrehozásához előbb töltsd ki a Feladói adatok menüpontban a szükséges céges adatokat.";
  }
  for (const [name, a] of [["Címzett", recipient.value]] as const) {
    if (m) break;
    if (
      !a.companyName ||
      !a.contactName ||
      !validCountry(a.countryCode) ||
      !a.postalCode ||
      !a.cityName ||
      !a.addressLine1 ||
      !a.phone ||
      (a.email && !/^\S+@\S+\.\S+$/.test(a.email))
    ) {
      m = `${name}: tölts ki minden kötelező mezőt, az országkód pedig érvényes ISO-2 kód legyen.`;
      break;
    }
  }
  if (
    !m &&
    packages.value.some((p) =>
      [p.weight, p.length, p.width, p.height].some((x) => !(Number(x) > 0)),
    )
  )
    m = "Minden csomagnál adj meg pozitív súlyt és méreteket.";
  if (
    !m &&
    effectiveCustomsDeclarable.value &&
    (!/^[A-Z]{3}$/.test(currency.value) ||
      !description.value ||
      items.value.some(
        (i) =>
          !i.description ||
          !(Number(i.quantity) > 0) ||
          !(Number(i.price) > 0) ||
          !validCountry(i.manufacturerCountry) ||
          !reasons.some((r) => r[0] === i.exportReasonType) ||
          (!(Number(i.netWeight) > 0) && !(Number(i.grossWeight) > 0)),
      ))
  )
    m = "Tölts ki minden kötelező vámadatot.";
  validation.value = m;
  return !m;
}
const base = () => ({
  sender: { ...sender.value, countryCode: country(sender.value.countryCode) },
  recipient: {
    ...recipient.value,
    countryCode: country(recipient.value.countryCode),
  },
  plannedShippingDateAndTime: `${pickupDate.value}T10:00:00`,
  customsDeclarable: effectiveCustomsDeclarable.value,
  packages: packages.value,
});
async function fileData(file: File) {
  return new Promise<string>((ok, fail) => {
    const r = new FileReader();
    r.onload = () => ok(String(r.result).split(",")[1]);
    r.onerror = fail;
    r.readAsDataURL(file);
  });
}
async function addFiles(list: FileList | File[]) {
  for (const file of Array.from(list)) {
    const format =
      file.type === "application/pdf"
        ? "PDF"
        : file.type === "image/png"
          ? "PNG"
          : file.type === "image/jpeg"
            ? "JPEG"
            : "";
    if (!format) {
      error.value = "Nem támogatott fájltípus. PDF, JPG vagy PNG tölthető fel.";
      continue;
    }
    if (file.size > 5 * 1024 * 1024) {
      error.value = "A fájl legfeljebb 5 MB lehet.";
      continue;
    }
    if (
      documents.value.some(
        (d) => d.file.name === file.name && d.file.size === file.size,
      )
    )
      continue;
    documents.value.push({ file, typeCode: "CIN", imageFormat: format });
  }
}
function drop(e: DragEvent) {
  e.preventDefault();
  if (e.dataTransfer?.files) addFiles(e.dataTransfer.files);
}
async function quote() {
  error.value = "";
  if (!valid()) return;
  quoteLoading.value = true;
  try {
    rates.value = await api("/api/shipments/quotes", {
      method: "POST",
      body: JSON.stringify(base()),
    });
    selected.value = undefined;
    stale.value = false;
  } catch (e) {
    error.value =
      e instanceof ApiError ? e.message : "A díjak lekérése nem sikerült.";
  } finally {
    quoteLoading.value = false;
  }
}
async function create() {
  if (!selected.value || stale.value) {
    validation.value = "Válassz egy aktuális szolgáltatást.";
    return;
  }
  if (!valid()) return;
  creating.value = true;
  error.value = "";
  try {
    const docs = await Promise.all(
      documents.value.map(async (d) => ({
        typeCode: d.typeCode,
        imageFormat: d.imageFormat,
        content: d.content || (await fileData(d.file)),
      })),
    );
    const body = {
      ...base(),
      productCode: selected.value.productCode,
      shipDate: pickupDate.value,
      description: effectiveCustomsDeclarable.value
        ? description.value
        : "Shipment",
      quotedDhlCost: selected.value.estimatedCost,
      currency: selected.value.currency,
      ...(effectiveCustomsDeclarable.value
        ? {
            declaredValue: declared.value,
            declaredValueCurrency: currency.value.toUpperCase(),
            incoterm: incoterm.value,
            exportDeclaration: {
              lineItems: items.value.map((i) => ({
                ...i,
                manufacturerCountry: country(i.manufacturerCountry),
              })),
              invoiceDate: invoiceDate.value,
            },
            customsDocuments: docs,
          }
        : {}),
    };
    created.value = await api("/api/shipments", {
      method: "POST",
      headers: { "Idempotency-Key": key },
      body: JSON.stringify(body),
    });
  } catch (e) {
    error.value =
      e instanceof ApiError
        ? e.message
        : "A küldemény létrehozása nem sikerült.";
  } finally {
    creating.value = false;
  }
}
function useProfile() {
  sender.value = {
    companyName: profile.value.companyName || "",
    contactName: profile.value.contactName || "",
    countryCode: profile.value.senderCountryCode || "",
    postalCode: profile.value.senderPostalCode || "",
    cityName: profile.value.senderCityName || "",
    addressLine1: profile.value.senderAddressLine1 || "",
    addressLine2: profile.value.senderAddressLine2 || "",
    stateOrProvinceCode: profile.value.senderStateOrProvinceCode || "",
    phone: profile.value.senderPhone || "",
    email: profile.value.email || "",
  };
}
async function download(x: any) {
  downloading.value = x.id;
  try {
    const b = await apiBlob(
        `/api/shipments/${created.value.id}/labels/${x.id}`,
      ),
      u = URL.createObjectURL(b),
      a = document.createElement("a");
    a.href = u;
    a.download = x.fileName;
    a.click();
    URL.revokeObjectURL(u);
  } finally {
    downloading.value = "";
  }
}
async function copy(x: string) {
  if (navigator.clipboard) {
    await navigator.clipboard.writeText(x);
    copied.value = x;
    setTimeout(() => (copied.value = ""), 1500);
  }
}
onMounted(async () => {
  try {
    profile.value = await api("/api/account/profile");
    useProfile();
  } finally {
    loadingProfile.value = false;
  }
});
</script>
<template>
  <section v-if="created" class="card success-state">
    <h1>Küldemény sikeresen létrehozva</h1>
    <p v-if="created.masterTrackingNumber">
      Master tracking: <b>{{ created.masterTrackingNumber }}</b>
      <button class="copy" @click="copy(created.masterTrackingNumber)">
        {{ copied === created.masterTrackingNumber ? "Másolva" : "Másolás" }}
      </button>
    </p>
    <p>
      {{ statusLabel(created.status) }} ·
      {{ money(created.customerPrice, created.currency) }}
    </p>
    <p v-for="p in created.packages" :key="p.packageIndex">
      Csomag {{ p.packageIndex }}: {{ p.trackingNumber }}
    </p>
    <div v-for="d in created.labels" :key="d.id" class="document">
      <span>{{ d.fileName }}</span
      ><button :disabled="downloading === d.id" @click="download(d)">
        Letöltés
      </button>
    </div>
    <RouterLink class="button" :to="`/shipments/${created.id}`"
      >Küldemény részletei</RouterLink
    >
    <RouterLink class="button secondary" to="/pickup">Futár rendelése</RouterLink>
  </section>
  <section v-else class="form">
    <div class="hero">
      <div>
        <h1>Új küldemény</h1>
        <p class="muted">
          Add meg az adatokat, kérj díjakat, majd válassz szolgáltatást.
        </p>
      </div>
    </div>
    <p class="muted">* Kötelező mező</p>
    <p v-if="error || validation" class="alert error">
      {{ error || validation }}
    </p>
    <section class="card">
      <div class="section-head">
        <h2>1. Feladó</h2>
        <RouterLink class="button secondary" to="/company-profile">Feladói adatok szerkesztése</RouterLink>
      </div>
      <p class="muted">A feladó automatikusan a mentett Feladói adatokból töltődik be.</p>
      <p v-if="!loadingProfile && !senderProfileComplete" class="alert error">
        A küldemény létrehozásához előbb töltsd ki a Feladói adatok menüpontban a szükséges céges adatokat.
      </p>
      <div class="address-grid">
        <label>Cégnév *<input :value="sender.companyName" readonly /></label
        ><label
          >Kapcsolattartó neve *<input :value="sender.contactName" readonly /></label
        ><label
          >Országkód *<input
            :value="sender.countryCode"
            readonly
          /><small>2 betűs ISO országkód, pl. HU, DE, US</small></label
        ><label
          >Állam / tartomány<input
            :value="sender.stateOrProvinceCode"
            readonly /></label
        ><label>Irányítószám *<input :value="sender.postalCode" readonly /></label
        ><label>Város *<input :value="sender.cityName" readonly /></label
        ><label>Cím *<input :value="sender.addressLine1" readonly /></label
        ><label>Cím 2<input :value="sender.addressLine2" readonly /></label
        ><label>Telefonszám *<input :value="sender.phone" readonly /></label
        ><label>E-mail *<input :value="sender.email" readonly /></label>
      </div>
    </section>
    <section class="card">
      <h2>2. Címzett</h2>
      <div class="address-grid">
        <label>Cégnév *<input v-model.trim="recipient.companyName" /></label
        ><label
          >Kapcsolattartó neve *<input
            v-model.trim="recipient.contactName" /></label
        ><label
          >Országkód *<input
            v-model="recipient.countryCode"
            maxlength="2"
            @input="recipient.countryCode = country(recipient.countryCode)"
          /><small>2 betűs ISO országkód, pl. HU, DE, US</small></label
        ><label
          >Állam / tartomány<input
            v-model.trim="recipient.stateOrProvinceCode"
            placeholder="pl. NC" /></label
        ><label>Irányítószám *<input v-model.trim="recipient.postalCode" /></label
        ><label>Város *<input v-model.trim="recipient.cityName" /></label
        ><label>Cím *<input v-model.trim="recipient.addressLine1" /></label
        ><label>Cím 2<input v-model.trim="recipient.addressLine2" /></label
        ><label>Telefonszám *<input v-model.trim="recipient.phone" /></label
        ><label>E-mail<input v-model.trim="recipient.email" /></label>
      </div>
    </section>
    <section class="card">
      <h2>3. Csomagok</h2>
      <p>{{ packages.length }} csomag · {{ total }} kg</p>
      <article v-for="(p, n) in packages" :key="n" class="package-form">
        <h3>Csomag {{ n + 1 }}</h3>
        <div class="package-grid">
          <label
            >Súly (kg) *<input v-model.number="p.weight" type="number" /></label
          ><label
            >Hossz (cm) *<input v-model.number="p.length" type="number" /></label
          ><label
            >Szélesség (cm) *<input
              v-model.number="p.width"
              type="number" /></label
          ><label
            >Magasság (cm) *<input v-model.number="p.height" type="number"
          /></label>
        </div>
        <button
          v-if="packages.length > 1"
          class="link-button"
          @click="packages.splice(n, 1)"
        >
          Csomag törlése
        </button>
      </article>
      <button class="secondary" @click="packages.push(blankP())">
        Csomag hozzáadása
      </button>
    </section>
    <section class="card">
      <h2>4. Vámadatok</h2>
      <label class="checkbox"
        ><input
          :checked="effectiveCustomsDeclarable"
          type="checkbox"
          @change="
            customsOverride = ($event.target as HTMLInputElement).checked
          "
        />Vámköteles küldemény</label
      >
      <p class="muted">
        A rendszer az országok alapján automatikusan beállítja, de szükség
        esetén módosítható.
      </p>
      <button
        v-if="customsOverride !== null"
        class="link-button"
        @click="customsOverride = null"
      >
        Automatikus beállítás visszaállítása
      </button>
      <template v-if="effectiveCustomsDeclarable"
        ><div class="address-grid">
          <label
            >Pénznem *<input
              v-model="currency"
              maxlength="3"
              @input="currency = currency.toUpperCase()" /></label
          ><label
            >Számla dátuma *<input v-model="invoiceDate" type="date" /></label
          ><label
            >Incoterm *<select v-model="incoterm">
              <option>DAP</option>
              <option>DDP</option>
              <option>EXW</option>
              <option>FCA</option>
            </select></label
          ><label>Küldemény leírása *<input v-model.trim="description" /></label>
        </div>
        <p>
          <b>Bejelentett összérték: {{ money(declared, currency) }}</b>
        </p>
        <article v-for="(i, n) in items" :key="i.number" class="customs-item">
          <h3>Vámtétel {{ n + 1 }}</h3>
          <div class="customs-grid">
            <label>Megnevezés *<input v-model="i.description" /></label
            ><label
              >Mennyiség *<input
                v-model.number="i.quantity"
                type="number" /></label
            ><label
              >Egységár *<input v-model.number="i.price" type="number" /></label
            ><label
              >Származási ország *<input
                v-model="i.manufacturerCountry"
                maxlength="2"
                @input="
                  i.manufacturerCountry = country(i.manufacturerCountry)
                " /></label
            ><label
              >Export oka *<select v-model="i.exportReasonType">
                <option v-for="r in reasons" :value="r[0]">{{ r[1] }}</option>
              </select></label
            ><label>HS-kód<input v-model="i.commodityCode" /></label
            ><label
              >Nettó tömeg (nettó vagy bruttó kötelező) *<input
                v-model.number="i.netWeight"
                type="number" /></label
            ><label
              >Bruttó tömeg (nettó vagy bruttó kötelező) *<input v-model.number="i.grossWeight" type="number"
            /></label>
          </div>
        </article>
        <section class="upload-zone" @dragover.prevent @drop="drop">
          <h3>Vámdokumentumok</h3>
          <p>
            A feltöltött dokumentumokat a rendszer elektronikusan továbbítja a
            DHL részére.
          </p>
          <input
            multiple
            accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
            type="file"
            @change="(e) => addFiles((e.target as HTMLInputElement).files!)"
          />
          <div
            v-for="(d, n) in documents"
            :key="d.file.name + d.file.size"
            class="document"
          >
            <span
              >{{ d.file.name }} ·
              {{ (d.file.size / 1024 / 1024).toFixed(2) }} MB</span
            ><select v-model="d.typeCode">
              <option v-for="t in docTypes" :value="t[0]">
                {{ t[1] }}
              </option></select
            ><button class="link-button" @click="documents.splice(n, 1)">
              Eltávolítás
            </button>
          </div>
        </section></template
      >
    </section>
    <section class="card">
      <h2>5. Szállítási szolgáltatás</h2>
      <p v-if="stale" class="alert error">
        A küldemény adatai megváltoztak. Kérj új szállítási díjakat.
      </p>
      <button :disabled="quoteLoading || loadingProfile || !senderProfileComplete" @click="quote">
        {{
          quoteLoading ? "Díjak lekérése…" : "Szállítási díjak lekérése"
        }}</button
      ><label v-for="r in rates" :key="r.productCode" class="quote-card"
        ><input v-model="selected" :value="r" type="radio" /><span
          ><b>{{ r.productName || productLabel(r.productCode) }}</b
          ><strong>{{ money(r.estimatedCost, r.currency) }}</strong
          ><em v-if="r.estimatedDelivery">{{
            dateTime(r.estimatedDelivery)
          }}</em></span
        ></label
      >
    </section>
    <section v-if="selected && !stale" class="card review">
      <h2>6. Ellenőrzés és létrehozás</h2>
      <p>
        Feladó: {{ sender.companyName }}, {{ sender.cityName }}
        {{ sender.countryCode }}
      </p>
      <p>
        Címzett: {{ recipient.companyName }}, {{ recipient.cityName }}
        {{ recipient.countryCode }}
      </p>
      <p>Csomagok: {{ packages.length }} db, {{ total }} kg</p>
      <p>
        Vámdokumentumok: {{ documents.length }} db
        <span v-for="d in documents"
          >{{ d.file.name }} ({{ d.typeCode }})
        </span>
      </p>
      <p>
        <b
          >Fizetendő szállítási díj:
          {{ money(selected.estimatedCost, selected.currency) }}</b
        >
      </p>
      <button :disabled="creating || !senderProfileComplete" @click="create">
        {{ creating ? "Küldemény létrehozása…" : "Küldemény létrehozása" }}
      </button>
    </section>
  </section>
</template>
