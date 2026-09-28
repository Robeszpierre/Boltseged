import { forceLogout, refreshAccessToken, token } from './auth'

export class ApiError extends Error {
  constructor(message: string, public status?: number, public details: string[] = []) {
    super(message)
  }
}

function authEndpoint(path: string) {
  return path.startsWith('/api/auth/')
}

async function unauthenticated(response: Response) {
  if (response.status === 401) return true
  if (response.status !== 403) return false
  try {
    const body = await response.clone().json()
    return body.code === 'UNAUTHENTICATED' || body.code === 'NOT_LOGGED_IN'
      || body.status === 'UNAUTHENTICATED' || body.status === 'NOT_LOGGED_IN'
  } catch {
    return false
  }
}

async function apiError(response: Response) {
  let body: any = {}
  try { body = await response.json() } catch {}
  const details = Array.isArray(body.details) ? body.details.filter((item: unknown): item is string => typeof item === 'string') : []
  const message = body.failureReason
    ? `${body.message || 'A küldemény létrehozása sikertelen volt.'}\n${body.failureReason}`
    : details.length
      ? `${body.message || 'A kérés nem sikerült.'}\n${details.join('\n')}`
      : body.message || 'A kérés nem sikerült.'
  return new ApiError(message, response.status, details)
}

async function request(path: string, init: RequestInit = {}, retried = false): Promise<Response> {
  const headers = {
    ...(init.body ? { 'Content-Type': 'application/json' } : {}),
    ...(token.value ? { Authorization: `Bearer ${token.value}` } : {}),
    ...(init.headers || {}),
  }
  const response = await fetch(path, { ...init, headers, credentials: 'same-origin' })
  if (!authEndpoint(path) && await unauthenticated(response)) {
    if (!retried && await refreshAccessToken()) return request(path, init, true)
    forceLogout()
    throw new ApiError('A munkamenet lejárt. Jelentkezz be újra.', 401)
  }
  if (!response.ok) throw await apiError(response)
  return response
}

export async function api(path: string, init: RequestInit = {}) {
  const response = await request(path, init)
  return response.status === 204 ? null : response.json()
}

export async function apiBlob(path: string, init: RequestInit = {}) {
  return (await request(path, init)).blob()
}
