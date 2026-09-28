import { ref } from 'vue'

type AuthResponse = { token: string; role: string }

export const token = ref('')
export const role = ref('')
export const authReady = ref(false)

let refreshInFlight: Promise<boolean> | undefined

export const loggedIn = () => Boolean(token.value && role.value)

export function setAuth(accessToken: string, userRole: string) {
  token.value = accessToken
  role.value = userRole
  localStorage.removeItem('token')
  localStorage.removeItem('role')
}

export function clearAuth() {
  token.value = ''
  role.value = ''
  localStorage.removeItem('token')
  localStorage.removeItem('role')
}

async function refresh() {
  try {
    const response = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' })
    if (!response.ok) return false
    const data = await response.json() as AuthResponse
    if (!data.token || !data.role) return false
    setAuth(data.token, data.role)
    return true
  } catch {
    return false
  }
}

function sharedRefresh() {
  if (!refreshInFlight) refreshInFlight = refresh().finally(() => { refreshInFlight = undefined })
  return refreshInFlight
}

export async function restoreSession() {
  const restored = await sharedRefresh()
  authReady.value = true
  if (!restored) clearAuth()
  return restored
}

export async function refreshAccessToken() {
  const restored = await sharedRefresh()
  if (!restored) forceLogout()
  return restored
}

export async function logout() {
  clearAuth()
  authReady.value = true
  try {
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' })
  } finally {
    forceLogout()
  }
}

export function forceLogout() {
  clearAuth()
  authReady.value = true
  if (location.pathname !== '/login') location.assign('/login')
}
