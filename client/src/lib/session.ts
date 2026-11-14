import type { AxiosError, AxiosInstance } from "axios"
import { refreshSession } from "@/api/auth"
import { PUSH_REMOVE_PATH } from "@/api/push"
import { getErrorStatus } from "@/lib/errors"
import type { AuthResponse } from "@/types/auth"

declare module "axios" {
  interface AxiosRequestConfig {
    // set on the one retry after a refresh, so a second 401 ends the session instead of looping
    sessionRetry?: boolean
    sessionGeneration?: number
  }
}

// The access token lives only in memory (never in localStorage, out of reach of a stored XSS payload); the
// refresh token is the HttpOnly futspring_refresh cookie, which the JS never sees. AuthContext owns the session
// state and is the only writer through setAccessToken / refreshAccessToken; the axios interceptors and the STOMP
// connection read the current token here.
let accessToken: string | null = null
let inFlight: Promise<AuthResponse> | null = null
// bumped by setAccessToken: a refresh answered after a logout or a new login doesn't bring the old session back
let generation = 0
let refreshController: AbortController | null = null
let onRefreshed: ((session: AuthResponse) => void) | null = null
let onExpired: (() => void) | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null) {
  accessToken = token
  generation += 1
  // Stop a late Set-Cookie from replacing the next login's refresh cookie.
  refreshController?.abort()
  refreshController = null
  inFlight = null
}

// AuthContext's listener: every refresh (the boot's, a 401's, the STOMP's) updates the session state
export function onSessionRefreshed(listener: (session: AuthResponse) => void): () => void {
  onRefreshed = listener
  return () => {
    if (onRefreshed === listener) onRefreshed = null
  }
}

// AuthContext's listener: the server ended the session (a refresh answered 401)
export function onSessionExpired(listener: () => void): () => void {
  onExpired = listener
  return () => {
    if (onExpired === listener) onExpired = null
  }
}

// Single flight per tab: concurrent callers (several 401s at once, the boot under StrictMode) share one
// POST /auth/refresh. Rejects with the request's error (401: the session ended; else the server couldn't answer)
class SessionChangedError extends Error {
  constructor() {
    super("A sessão mudou. A operação foi cancelada.")
    this.name = "SessionChangedError"
  }
}

function requireCurrentSession(started: number | undefined) {
  if (started !== generation) throw new SessionChangedError()
}

export function refreshAccessToken(): Promise<AuthResponse> {
  if (!inFlight) {
    const started = generation
    refreshController = new AbortController()
    const pending = refreshSession(refreshController.signal)
      .then((session) => {
        requireCurrentSession(started)
        accessToken = session.token
        onRefreshed?.(session)
        return session
      })
      .catch((error: unknown) => {
        // An old refresh's 401 must not expire a newer login either.
        requireCurrentSession(started)
        throw error
      })
      .finally(() => {
        // A new login can already have started its own refresh.
        if (inFlight === pending) {
          inFlight = null
          refreshController = null
        }
      })
    inFlight = pending
  }
  return inFlight
}

// Seconds since the epoch of the JWT's `exp`, read without checking the signature (only to decide when to
// refresh; the server is the authority); null when the token can't be read
export function tokenExpiry(token: string): number | null {
  try {
    const payload = token.split(".")[1]
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"))
    const exp = (JSON.parse(json) as { exp?: unknown }).exp
    return typeof exp === "number" ? exp : null
  } catch {
    return null
  }
}

// A token still valid for at least `marginMs`, refreshing first when it isn't (the STOMP CONNECT has no 401 to
// retry on). A failed refresh answers the current token: the server refuses it and the reconnect logic takes over
export async function freshAccessToken(marginMs = 30_000, now = Date.now()): Promise<string | null> {
  const expiry = accessToken ? tokenExpiry(accessToken) : null
  if (accessToken && expiry !== null && expiry * 1000 - now > marginMs) return accessToken
  try {
    await refreshAccessToken()
  } catch {
    // answered below with what there is
  }
  return accessToken
}

// A 401 from these is not an expired session: a wrong password or similar shown by the form (/auth/*, the refresh
// included), or the logout's push removal sent with the closing session's token after a reset or deletion
function isExempt(url: string | undefined): boolean {
  return url?.startsWith("/api/v1/auth/") === true || url === PUSH_REMOVE_PATH
}

// Called once when AuthContext loads (not in an effect, so the shell's first requests already go through them).
// Request: the current access token, unless the call set its own (the logout's push removal). Response: a 401
// refreshes once (shared by every request that got one) and repeats the request once with the new token; a
// refresh answering 401, or a repeat answering 401 again, ends the session. A refresh that fails otherwise (no
// network, 503) keeps the session and rejects with the original error, so the screen shows its error state.
export function installSessionInterceptors(client: AxiosInstance) {
  client.interceptors.request.use((config) => {
    config.sessionGeneration ??= generation
    if (!isExempt(config.url)) requireCurrentSession(config.sessionGeneration)
    if (accessToken && !config.headers.has("Authorization")) {
      config.headers.set("Authorization", `Bearer ${accessToken}`)
    }
    return config
  })
  client.interceptors.response.use((response) => {
    if (!isExempt(response.config.url)) requireCurrentSession(response.config.sessionGeneration)
    return response
  }, async (error: AxiosError) => {
    const config = error.config
    if (error.response?.status !== 401 || !config || isExempt(config.url)) throw error
    requireCurrentSession(config.sessionGeneration)
    if (config.sessionRetry) {
      onExpired?.()
      throw error
    }
    let token: string
    try {
      // A delayed 401 may still carry the JWT replaced by an earlier refresh in this same session.
      token = accessToken && config.headers.get("Authorization") !== `Bearer ${accessToken}`
        ? accessToken
        : (await refreshAccessToken()).token
    } catch (refreshError) {
      requireCurrentSession(config.sessionGeneration)
      if (getErrorStatus(refreshError) === 401) onExpired?.()
      throw error
    }
    requireCurrentSession(config.sessionGeneration)
    config.sessionRetry = true
    config.headers.set("Authorization", `Bearer ${token}`)
    return client.request(config)
  })
}
