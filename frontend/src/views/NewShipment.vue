<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
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
type Decimal = number | string | null;
type P = {
  weight: Decimal;
  length: Decimal;
  width: Decimal;
  height: Decimal;
};
type I = {
  number: number;
  description: string;
  quantity: number | null;
  quantityUnitOfMeasurement: string;
  price: Decimal;
  manufacturerCountry: string;
  commodityCode: string;
  netWeight: Decimal;
  grossWeight: Decimal;
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
type CopySource = {
  productCode?: string;
  recipient?: Partial<A>;
  packages?: P[];
  customs?: {
    customsDeclarable?: boolean;
    description?: string;
    declaredValueCurrency?: string;
    incoterm?: string;
    lineItems?: Omit<I, "number">[];
  };
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
  quoteError = ref(""),
  createError = ref(""),
  senderError = ref(""),
  fieldErrors = ref<Record<string, string>>({}),
  validationStarted = ref(false),
  stale = ref(false),
  created = ref<any>(),
  downloading = ref(""),
  copied = ref("");
const route = useRoute(),
  copyLoading = ref(false),
  copyNotice = ref(""),
  copiedProductCode = ref("");
let idempotencyKey: string | undefined;
function newIdempotencyKey() {
  if (crypto.randomUUID) return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
function attemptKey() {
  return idempotencyKey ??= newIdempotencyKey();
}
const fieldElements = new Map<string, HTMLElement>();
const actionElements = new Map<string, HTMLElement>();
const fieldRef = (path: string) => (element: unknown) => {
  if (element instanceof HTMLElement) fieldElements.set(path, element);
  else fieldElements.delete(path);
};
const actionRef = (name: string) => (element: unknown) => {
  if (element instanceof HTMLElement) actionElements.set(name, element);
  else actionElements.delete(name);
};
const hasError = (path: string) => Boolean(fieldErrors.value[path]);
const errorFor = (path: string) => fieldErrors.value[path];
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
watch(
  [recipient, packages, items, currency, incoterm, description, invoiceDate, effectiveCustomsDeclarable],
  () => {
    if (validationStarted.value) validateFields();
  },
  { deep: true },
);
const total = computed(() =>
    packages.value.reduce((n, p) => n + (decimalNumber(p.weight) || 0), 0),
  ),
  declared = computed(() =>
    items.value.reduce(
      (n, i) => n + (decimalNumber(i.price) || 0) * (Number(i.quantity) || 0),
      0,
    ),
  );
function decimalNumber(value: Decimal) {
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  const normalized = value?.trim().replace(",", ".") || "";
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}
function decimalInput(event: Event) {
  const input = event.target as HTMLInputElement;
  let separator = false;
  input.value = [...input.value].filter((character) => {
    if (/\d/.test(character)) return true;
    if ((character === "," || character === ".") && !separator) {
      separator = true;
      return true;
    }
    return false;
  }).join("");
  return input.value;
}
const country = (v: string) => v.trim().toUpperCase();
const validCountry = (v: string) => ISO2.has(country(v));
function addError(path: string, message: string) {
  if (!fieldErrors.value[path]) fieldErrors.value[path] = message;
}
async function focusField(path: string) {
  await nextTick();
  const element = fieldElements.get(path);
  element?.scrollIntoView({ behavior: "smooth", block: "center" });
  element?.focus({ preventScroll: true });
}
async function focusAction(name: string) {
  await nextTick();
  actionElements.get(name)?.scrollIntoView({ behavior: "smooth", block: "center" });
}
function validateFields() {
  validationStarted.value = true;
  fieldErrors.value = {};
  const a = recipient.value;
  if (!a.companyName) addError("recipient.companyName", "Add meg a címzett cégnevét.");
  if (!a.contactName) addError("recipient.contactName", "Add meg a címzett kapcsolattartójának nevét.");
  if (!a.countryCode) addError("recipient.countryCode", "Add meg a címzett országkódját.");
  else if (!validCountry(a.countryCode)) addError("recipient.countryCode", "Adj meg érvényes, kétbetűs ISO országkódot.");
  if (!a.stateOrProvinceCode && country(a.countryCode) === "US") addError("recipient.stateOrProvinceCode", "Amerikai címzettnél add meg a kétbetűs államkódot.");
  else if (a.stateOrProvinceCode && country(a.countryCode) === "US" && !/^[A-Za-z]{2}$/.test(a.stateOrProvinceCode)) addError("recipient.stateOrProvinceCode", "Az államkód pontosan két betű legyen.");
  if (!a.postalCode) addError("recipient.postalCode", "Add meg a címzett irányítószámát.");
  if (!a.cityName) addError("recipient.cityName", "Add meg a címzett városát.");
  if (!a.addressLine1) addError("recipient.addressLine1", "Add meg a címzett címét.");
  if (!a.phone) addError("recipient.phone", "Add meg a címzett telefonszámát.");
  if (a.email && !/^\S+@\S+\.\S+$/.test(a.email)) addError("recipient.email", "Adj meg érvényes e-mail-címet.");
  packages.value.forEach((p, index) => {
    (["weight", "length", "width", "height"] as const).forEach((field) => {
      const value = p[field];
      const labels = { weight: "súlyát", length: "hosszát", width: "szélességét", height: "magasságát" };
      if (value === null || value === undefined || value === "") addError(`packages.${index}.${field}`, `Add meg a csomag ${labels[field]}.`);
      else if (!(decimalNumber(value) && decimalNumber(value)! > 0)) addError(`packages.${index}.${field}`, `A csomag ${labels[field]} 0-nál nagyobb legyen.`);
    });
  });
  if (effectiveCustomsDeclarable.value) {
    if (!/^[A-Z]{3}$/.test(currency.value)) addError("customs.currency", "Adj meg hárombetűs pénznemkódot.");
    if (!invoiceDate.value) addError("customs.invoiceDate", "Add meg a számla dátumát.");
    if (!incoterm.value) addError("customs.incoterm", "Válassz Incotermet.");
    if (!description.value) addError("customs.description", "Add meg a küldemény leírását.");
    else if (description.value.length > 70) addError("customs.description", "A küldemény tartalmának leírása legfeljebb 70 karakter lehet.");
    items.value.forEach((item, index) => {
      const path = `exportDeclaration.lineItems.${index}`;
      if (!item.description) addError(`${path}.description`, "Add meg a vámtétel megnevezését.");
      if (item.quantity === null || item.quantity === undefined) addError(`${path}.quantity`, "Add meg a mennyiséget.");
      else if (!(Number(item.quantity) > 0)) addError(`${path}.quantity`, "A mennyiség 0-nál nagyobb legyen.");
      if (item.price === null || item.price === undefined) addError(`${path}.price`, "Add meg az egységárat.");
      else if (!(decimalNumber(item.price) && decimalNumber(item.price)! > 0)) addError(`${path}.price`, "Az egységár 0-nál nagyobb legyen.");
      if (!validCountry(item.manufacturerCountry)) addError(`${path}.manufacturerCountry`, "Adj meg érvényes, kétbetűs származási országkódot.");
      if (!item.exportReasonType) addError(`${path}.exportReasonType`, "Válaszd ki az export okát.");
      const netWeight = decimalNumber(item.netWeight), grossWeight = decimalNumber(item.grossWeight);
      if (item.netWeight === null || item.netWeight === undefined || item.netWeight === "") addError(`${path}.netWeight`, "Add meg a tétel nettó tömegét.");
      else if (!(netWeight && netWeight > 0)) addError(`${path}.netWeight`, "A nettó tömegnek 0-nál nagyobbnak kell lennie.");
      if (item.grossWeight === null || item.grossWeight === undefined || item.grossWeight === "") addError(`${path}.grossWeight`, "Add meg a tétel bruttó tömegét.");
      else if (!(grossWeight && grossWeight > 0)) addError(`${path}.grossWeight`, "A bruttó tömegnek 0-nál nagyobbnak kell lennie.");
      else if (netWeight && grossWeight < netWeight) addError(`${path}.grossWeight`, "A bruttó tömeg nem lehet kisebb a nettó tömegnél.");
    });
  }
  return Object.keys(fieldErrors.value);
}
async function validateAndFocus() {
  const paths = validateFields();
  if (paths.length) {
    await focusField(paths[0]);
    return false;
  }
  if (!senderProfileComplete.value) {
    senderError.value = "A küldemény létrehozásához előbb töltsd ki a Feladói adatok menüpontban a szükséges céges adatokat.";
    await focusAction("sender");
    return false;
  }
  return true;
}
function mapBackendErrors(error: ApiError) {
  const messages = error.details.length ? error.details : [error.message];
  fieldErrors.value = {};
  for (const message of messages) {
    const value = message.toLowerCase();
    const lineItem = value.match(/lineitems?[.\[](\d+)[\].]([a-z]+)/);
    const parcel = value.match(/packages?[.\[](\d+)[\].]([a-z]+)/);
    const lineFields: Record<string, string> = { description: "description", quantity: "quantity", price: "price", manufacturercountry: "manufacturerCountry", exportreasontype: "exportReasonType", netweight: "netWeight", grossweight: "grossWeight" };
    if (lineItem && lineFields[lineItem[2]]) addError(`exportDeclaration.lineItems.${lineItem[1]}.${lineFields[lineItem[2]]}`, message);
    else if (parcel && ["weight", "length", "width", "height"].includes(parcel[2])) addError(`packages.${parcel[1]}.${parcel[2]}`, message);
    else if (value.includes("declaredvaluecurrency") || value.includes("currency")) addError("customs.currency", message);
    else if (value.includes("incoterm")) addError("customs.incoterm", message);
    else if (value.includes("államkód") || value.includes("stateorprovince")) addError("recipient.stateOrProvinceCode", message);
    else if (value.includes("irányítószám") || value.includes("postal")) addError("recipient.postalCode", message);
    else if (value.includes("város") || value.includes("city")) addError("recipient.cityName", message);
    else if (value.includes("címzett cím") || value.includes("addressline1")) addError("recipient.addressLine1", message);
  }
  return Object.keys(fieldErrors.value);
}
const base = () => ({
  sender: { ...sender.value, countryCode: country(sender.value.countryCode) },
  recipient: {
    ...recipient.value,
    countryCode: country(recipient.value.countryCode),
  },
  plannedShippingDateAndTime: `${pickupDate.value}T10:00:00`,
  customsDeclarable: effectiveCustomsDeclarable.value,
  packages: packages.value.map((item) => ({
    weight: decimalNumber(item.weight),
    length: decimalNumber(item.length),
    width: decimalNumber(item.width),
    height: decimalNumber(item.height),
  })),
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
      quoteError.value = "Nem támogatott fájltípus. PDF, JPG vagy PNG tölthető fel.";
      continue;
    }
    if (file.size > 5 * 1024 * 1024) {
      quoteError.value = "A fájl legfeljebb 5 MB lehet.";
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
  quoteError.value = "";
  createError.value = "";
  senderError.value = "";
  if (!(await validateAndFocus())) return;
  quoteLoading.value = true;
  try {
    rates.value = await api("/api/shipments/quotes", {
      method: "POST",
      body: JSON.stringify(base()),
    });
    selected.value = copiedProductCode.value
      ? rates.value.find((rate) => rate.productCode === copiedProductCode.value)
      : undefined;
    stale.value = false;
  } catch (e) {
    if (e instanceof ApiError) {
      const paths = mapBackendErrors(e);
      if (paths.length) await focusField(paths[0]);
      else {
        quoteError.value = e.message;
        await focusAction("quote");
      }
    } else {
      quoteError.value = "A díjak lekérése nem sikerült.";
      await focusAction("quote");
    }
  } finally {
    quoteLoading.value = false;
  }
}
async function create() {
  if (creating.value) return;
  createError.value = "";
  quoteError.value = "";
  senderError.value = "";
  if (!selected.value || stale.value) {
    quoteError.value = "Válassz egy aktuális szolgáltatást.";
    await focusAction("quote");
    return;
  }
  if (!(await validateAndFocus())) return;
  creating.value = true;
  const key = attemptKey();
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
                price: decimalNumber(i.price),
                netWeight: decimalNumber(i.netWeight),
                grossWeight: decimalNumber(i.grossWeight),
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
    if (e instanceof ApiError) {
      idempotencyKey = undefined;
      const paths = mapBackendErrors(e);
      if (paths.length) await focusField(paths[0]);
      else {
        createError.value = e.message;
        await focusAction("create");
      }
    } else {
      createError.value = "A küldemény létrehozása nem sikerült.";
      await focusAction("create");
    }
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
function numberOrNull(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}
async function loadCopySource() {
  const sourceId = typeof route.query.copy === "string" ? route.query.copy : "";
  if (!sourceId) return;
  copyLoading.value = true;
  copyNotice.value = "";
  try {
    const source = (await api(`/api/shipments/${encodeURIComponent(sourceId)}/copy-source`)) as CopySource;
    recipient.value = { ...blankA(), ...source.recipient };
    packages.value = source.packages?.length
      ? source.packages.map((item) => ({
          weight: numberOrNull(item.weight),
          length: numberOrNull(item.length),
          width: numberOrNull(item.width),
          height: numberOrNull(item.height),
        }))
      : [blankP()];
    customsOverride.value = source.customs?.customsDeclarable ?? null;
    description.value = source.customs?.description || "";
    currency.value = source.customs?.declaredValueCurrency || "";
    incoterm.value = source.customs?.incoterm || "DAP";
    items.value = source.customs?.lineItems?.length
      ? source.customs.lineItems.map((item, index) => ({
          number: index + 1,
          description: item.description || "",
          quantity: numberOrNull(item.quantity),
          quantityUnitOfMeasurement: item.quantityUnitOfMeasurement || "PCS",
          price: numberOrNull(item.price),
          manufacturerCountry: item.manufacturerCountry || "",
          commodityCode: item.commodityCode || "",
          netWeight: numberOrNull(item.netWeight),
          grossWeight: numberOrNull(item.grossWeight),
          exportReasonType: item.exportReasonType || "",
        }))
      : [blankI(1)];
    documents.value = [];
    rates.value = [];
    selected.value = undefined;
    stale.value = false;
    copiedProductCode.value = source.productCode || "";
    copyNotice.value = "Egy korábbi küldemény adatait másoltuk be. Ellenőrizd az adatokat, majd kérj új díjat.";
  } catch (e) {
    quoteError.value = e instanceof ApiError
      ? `A másolandó küldemény nem tölthető be: ${e.message}`
      : "A másolandó küldemény nem tölthető be. Az új küldemény űrlapja továbbra is használható.";
  } finally {
    copyLoading.value = false;
  }
}
onMounted(async () => {
  try {
    profile.value = await api("/api/account/profile");
    useProfile();
    await loadCopySource();
  } finally {
    loadingProfile.value = false;
  }
});
</script>
<template>
  <section v-if="copyLoading" class="card empty">A korábbi küldemény adatainak betöltése…</section>
  <section v-else-if="created" class="card success-state">
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
    <p v-if="copyNotice" class="alert success-text">{{ copyNotice }}</p>
    <section ref="actionRef('sender')" class="card">
      <div class="section-head">
        <h2>1. Feladó</h2>
        <RouterLink class="button secondary" to="/company-profile">Feladói adatok szerkesztése</RouterLink>
      </div>
      <p class="muted">A feladó automatikusan a mentett Feladói adatokból töltődik be.</p>
      <p v-if="!loadingProfile && !senderProfileComplete" class="alert error">
        A küldemény létrehozásához előbb töltsd ki a Feladói adatok menüpontban a szükséges céges adatokat.
      </p>
      <p v-else-if="senderError" class="alert error">{{ senderError }}</p>
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
        <label>Cégnév *<input :ref="fieldRef('recipient.companyName')" :class="{ 'field-invalid': hasError('recipient.companyName') }" v-model.trim="recipient.companyName" /><small v-if="errorFor('recipient.companyName')" class="field-error">{{ errorFor('recipient.companyName') }}</small></label
        ><label
          >Kapcsolattartó neve *<input
            :ref="fieldRef('recipient.contactName')" :class="{ 'field-invalid': hasError('recipient.contactName') }" v-model.trim="recipient.contactName" /><small v-if="errorFor('recipient.contactName')" class="field-error">{{ errorFor('recipient.contactName') }}</small></label
        ><label
          >Országkód *<input
            :ref="fieldRef('recipient.countryCode')" :class="{ 'field-invalid': hasError('recipient.countryCode') }" v-model="recipient.countryCode"
            maxlength="2"
            @input="recipient.countryCode = country(recipient.countryCode)"
          /><small>2 betűs ISO országkód, pl. HU, DE, US</small><small v-if="errorFor('recipient.countryCode')" class="field-error">{{ errorFor('recipient.countryCode') }}</small></label
        ><label
          >Állam / tartomány<input
            :ref="fieldRef('recipient.stateOrProvinceCode')" :class="{ 'field-invalid': hasError('recipient.stateOrProvinceCode') }" v-model.trim="recipient.stateOrProvinceCode"
            placeholder="pl. NC" /><small v-if="errorFor('recipient.stateOrProvinceCode')" class="field-error">{{ errorFor('recipient.stateOrProvinceCode') }}</small></label
        ><label>Irányítószám *<input :ref="fieldRef('recipient.postalCode')" :class="{ 'field-invalid': hasError('recipient.postalCode') }" v-model.trim="recipient.postalCode" /><small v-if="errorFor('recipient.postalCode')" class="field-error">{{ errorFor('recipient.postalCode') }}</small></label
        ><label>Város *<input :ref="fieldRef('recipient.cityName')" :class="{ 'field-invalid': hasError('recipient.cityName') }" v-model.trim="recipient.cityName" /><small v-if="errorFor('recipient.cityName')" class="field-error">{{ errorFor('recipient.cityName') }}</small></label
        ><label>Cím *<input :ref="fieldRef('recipient.addressLine1')" :class="{ 'field-invalid': hasError('recipient.addressLine1') }" v-model.trim="recipient.addressLine1" /><small v-if="errorFor('recipient.addressLine1')" class="field-error">{{ errorFor('recipient.addressLine1') }}</small></label
        ><label>Cím 2<input v-model.trim="recipient.addressLine2" /></label
        ><label>Telefonszám *<input :ref="fieldRef('recipient.phone')" :class="{ 'field-invalid': hasError('recipient.phone') }" v-model.trim="recipient.phone" /><small v-if="errorFor('recipient.phone')" class="field-error">{{ errorFor('recipient.phone') }}</small></label
        ><label>E-mail<input :ref="fieldRef('recipient.email')" :class="{ 'field-invalid': hasError('recipient.email') }" v-model.trim="recipient.email" /><small v-if="errorFor('recipient.email')" class="field-error">{{ errorFor('recipient.email') }}</small></label>
      </div>
    </section>
    <section class="card">
      <h2>3. Csomagok</h2>
      <p>{{ packages.length }} csomag · {{ total }} kg</p>
      <article v-for="(p, n) in packages" :key="n" class="package-form">
        <h3>Csomag {{ n + 1 }}</h3>
        <div class="package-grid">
          <label
            >Csomag teljes súlya (kg) *<input :ref="fieldRef(`packages.${n}.weight`)" :class="{ 'field-invalid': hasError(`packages.${n}.weight`) }" :value="p.weight ?? ''" inputmode="decimal" @input="p.weight = decimalInput($event)" /><small>A csomag teljes súlya csomagolással együtt.</small><small v-if="errorFor(`packages.${n}.weight`)" class="field-error">{{ errorFor(`packages.${n}.weight`) }}</small></label
          ><label
            >Hossz (cm) *<input :ref="fieldRef(`packages.${n}.length`)" :class="{ 'field-invalid': hasError(`packages.${n}.length`) }" :value="p.length ?? ''" inputmode="decimal" @input="p.length = decimalInput($event)" /><small v-if="errorFor(`packages.${n}.length`)" class="field-error">{{ errorFor(`packages.${n}.length`) }}</small></label
          ><label
            >Szélesség (cm) *<input :ref="fieldRef(`packages.${n}.width`)" :class="{ 'field-invalid': hasError(`packages.${n}.width`) }"
              :value="p.width ?? ''" inputmode="decimal" @input="p.width = decimalInput($event)" /><small v-if="errorFor(`packages.${n}.width`)" class="field-error">{{ errorFor(`packages.${n}.width`) }}</small></label
          ><label
            >Magasság (cm) *<input :ref="fieldRef(`packages.${n}.height`)" :class="{ 'field-invalid': hasError(`packages.${n}.height`) }" :value="p.height ?? ''" inputmode="decimal" @input="p.height = decimalInput($event)"
          /><small v-if="errorFor(`packages.${n}.height`)" class="field-error">{{ errorFor(`packages.${n}.height`) }}</small></label>
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
            >Pénznem *<input :ref="fieldRef('customs.currency')" :class="{ 'field-invalid': hasError('customs.currency') }"
              v-model="currency"
              maxlength="3"
              @input="currency = currency.toUpperCase()" /><small v-if="errorFor('customs.currency')" class="field-error">{{ errorFor('customs.currency') }}</small></label
          ><label
            >Számla dátuma *<input :ref="fieldRef('customs.invoiceDate')" :class="{ 'field-invalid': hasError('customs.invoiceDate') }" v-model="invoiceDate" type="date" /><small v-if="errorFor('customs.invoiceDate')" class="field-error">{{ errorFor('customs.invoiceDate') }}</small></label
          ><label
            >Incoterm *<select :ref="fieldRef('customs.incoterm')" :class="{ 'field-invalid': hasError('customs.incoterm') }" v-model="incoterm">
              <option>DAP</option>
              <option>DDP</option>
              <option>EXW</option>
              <option>FCA</option>
            </select><small v-if="errorFor('customs.incoterm')" class="field-error">{{ errorFor('customs.incoterm') }}</small></label
          ><label>Küldemény leírása *<input :ref="fieldRef('customs.description')" :class="{ 'field-invalid': hasError('customs.description') }" v-model.trim="description" maxlength="70" /><small>{{ description.length }} / 70</small><small v-if="errorFor('customs.description')" class="field-error">{{ errorFor('customs.description') }}</small></label>
        </div>
        <p>
          <b>Bejelentett összérték: {{ money(declared, currency) }}</b>
        </p>
        <article v-for="(i, n) in items" :key="i.number" class="customs-item">
          <h3>Vámtétel {{ n + 1 }}</h3>
          <div class="customs-grid">
            <label>Megnevezés *<input :ref="fieldRef(`exportDeclaration.lineItems.${n}.description`)" :class="{ 'field-invalid': hasError(`exportDeclaration.lineItems.${n}.description`) }" v-model="i.description" /><small v-if="errorFor(`exportDeclaration.lineItems.${n}.description`)" class="field-error">{{ errorFor(`exportDeclaration.lineItems.${n}.description`) }}</small></label
            ><label
              >Mennyiség *<input
              :ref="fieldRef(`exportDeclaration.lineItems.${n}.quantity`)" :class="{ 'field-invalid': hasError(`exportDeclaration.lineItems.${n}.quantity`) }" v-model.number="i.quantity"
              type="number" /><small v-if="errorFor(`exportDeclaration.lineItems.${n}.quantity`)" class="field-error">{{ errorFor(`exportDeclaration.lineItems.${n}.quantity`) }}</small></label
            ><label
              >Egységár *<input :ref="fieldRef(`exportDeclaration.lineItems.${n}.price`)" :class="{ 'field-invalid': hasError(`exportDeclaration.lineItems.${n}.price`) }" :value="i.price ?? ''" inputmode="decimal" @input="i.price = decimalInput($event)" /><small v-if="errorFor(`exportDeclaration.lineItems.${n}.price`)" class="field-error">{{ errorFor(`exportDeclaration.lineItems.${n}.price`) }}</small></label
            ><label
              >Származási ország *<input
              :ref="fieldRef(`exportDeclaration.lineItems.${n}.manufacturerCountry`)" :class="{ 'field-invalid': hasError(`exportDeclaration.lineItems.${n}.manufacturerCountry`) }" v-model="i.manufacturerCountry"
                maxlength="2"
                @input="
                  i.manufacturerCountry = country(i.manufacturerCountry)
                " /><small v-if="errorFor(`exportDeclaration.lineItems.${n}.manufacturerCountry`)" class="field-error">{{ errorFor(`exportDeclaration.lineItems.${n}.manufacturerCountry`) }}</small></label
            ><label
              >Export oka *<select :ref="fieldRef(`exportDeclaration.lineItems.${n}.exportReasonType`)" :class="{ 'field-invalid': hasError(`exportDeclaration.lineItems.${n}.exportReasonType`) }" v-model="i.exportReasonType">
                <option v-for="r in reasons" :value="r[0]">{{ r[1] }}</option>
              </select><small v-if="errorFor(`exportDeclaration.lineItems.${n}.exportReasonType`)" class="field-error">{{ errorFor(`exportDeclaration.lineItems.${n}.exportReasonType`) }}</small></label
            ><label>HS-kód<input v-model="i.commodityCode" /></label
            ><label
              >Tétel nettó tömege (kg) *<input
              :ref="fieldRef(`exportDeclaration.lineItems.${n}.netWeight`)" :class="{ 'field-invalid': hasError(`exportDeclaration.lineItems.${n}.netWeight`) }" :value="i.netWeight ?? ''" inputmode="decimal" @input="i.netWeight = decimalInput($event)"
              /><small>Nettó: az áru tömege csomagolás nélkül.</small><small v-if="errorFor(`exportDeclaration.lineItems.${n}.netWeight`)" class="field-error">{{ errorFor(`exportDeclaration.lineItems.${n}.netWeight`) }}</small></label
            ><label
              >Tétel bruttó tömege (kg) *<input :ref="fieldRef(`exportDeclaration.lineItems.${n}.grossWeight`)" :class="{ 'field-invalid': hasError(`exportDeclaration.lineItems.${n}.grossWeight`) }" :value="i.grossWeight ?? ''" inputmode="decimal" @input="i.grossWeight = decimalInput($event)"
            /><small>Bruttó: az áru vámáru-nyilatkozathoz használt teljes tömege.</small><small v-if="errorFor(`exportDeclaration.lineItems.${n}.grossWeight`)" class="field-error">{{ errorFor(`exportDeclaration.lineItems.${n}.grossWeight`) }}</small></label>
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
    <section ref="actionRef('quote')" class="card">
      <h2>5. Szállítási szolgáltatás</h2>
      <p v-if="quoteError" class="alert error action-error">{{ quoteError }}</p>
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
    <section v-if="selected && !stale" ref="actionRef('create')" class="card review">
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
      <p v-if="createError" class="alert error action-error">{{ createError }}</p>
    </section>
  </section>
</template>
