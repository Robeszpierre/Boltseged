export const statusLabel=(s:string)=>({CREATED:'Létrehozva',PENDING:'Feldolgozás alatt',FAILED:'Sikertelen'}[s]||s)
export const billingStatusLabel=(s:string)=>({UNBILLED:'Számlázásra vár',INVOICED:'Számlázva'}[s]||s)
export const money=(value:number,currency:string)=>{try{return new Intl.NumberFormat('hu-HU',{style:'currency',currency}).format(value)}catch{return `${new Intl.NumberFormat('hu-HU',{minimumFractionDigits:2,maximumFractionDigits:2}).format(value)} ${currency||''}`.trim()}}
export const dateTime=(value:string)=>new Intl.DateTimeFormat('hu-HU',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}).format(new Date(value))
export const productLabel=(code?:string)=>code?`DHL ${code}`:'DHL szolgáltatás'
