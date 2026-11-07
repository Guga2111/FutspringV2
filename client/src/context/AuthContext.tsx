import { useCallback, useEffect, useMemo, useState } from "react"
import type { ReactNode } from "react"
import type { UserResponseDTO } from "@/types/auth"
import apiClient from "@/api/client"
import { PUSH_REMOVE_PATH, unsubscribePush } from "@/api/push"
import { currentPushSubscription, setAppBadge } from "@/lib/push"
import { AuthContext } from "./auth-context-value"

const TOKEN_KEY = "futspring_token"
const USER_KEY = "futspring_user"
// how long a logout waits for the server to forget this device's push subscription
const PUSH_REMOVE_TIMEOUT_MS = 2000

// This device stops receiving the user's pushes: the server forgets the subscription (best effort, with the token
// of the session being closed) and the browser drops it. Without a token (an expired session) only the browser
// drops it; the server deletes the row at the push service's first 410.
async function releasePushDevice(token: string | null) {
  setAppBadge(0)
  try {
    const subscription = await currentPushSubscription()
    if (!subscription) return
    if (token) await unsubscribePush(subscription.endpoint, { token, timeout: PUSH_REMOVE_TIMEOUT_MS }).catch(() => undefined)
    await subscription.unsubscribe()
  } catch {
    // nothing to release on this browser
  }
}

// Registered once when the module loads, not in an effect: child effects (AppLayout's first fetch) run before
// the provider's own effects, so an effect-registered interceptor would miss the first requests.
apiClient.interceptors.request.use((config) => {
  const storedToken = localStorage.getItem(TOKEN_KEY)
  if (storedToken) {
    config.headers.Authorization = `Bearer ${storedToken}`
  }
  return config
})

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))
  const [user, setUser] = useState<UserResponseDTO | null>(() => {
    const stored = localStorage.getItem(USER_KEY)
    return stored ? (JSON.parse(stored) as UserResponseDTO) : null
  })

  const login = useCallback((newToken: string, newUser: UserResponseDTO) => {
    setToken(newToken)
    setUser(newUser)
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_KEY, JSON.stringify(newUser))
  }, [])

  const updateUser = useCallback((patch: Partial<UserResponseDTO>) => {
    setUser((current) => {
      if (!current) return current
      const next = { ...current, ...patch }
      localStorage.setItem(USER_KEY, JSON.stringify(next))
      return next
    })
  }, [])

  // `sessionValid`: false after a 401, when the server would refuse the push removal anyway
  const endSession = useCallback((sessionValid: boolean) => {
    void releasePushDevice(sessionValid ? localStorage.getItem(TOKEN_KEY) : null)
    setToken(null)
    setUser(null)
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }, [])

  const logout = useCallback(() => endSession(true), [endSession])

  // The 401 interceptor lives inside the provider so it has access to logout
  useEffect(() => {
    const responseInterceptor = apiClient.interceptors.response.use(
      (response) => response,
      (error) => {
        // A 401 from login/register is a wrong password or similar, shown by the form, not an expired session;
        // one from the logout's push removal (a password reset or deletion already revoked the token) is ignored
        const url: string | undefined = error.config?.url
        const isAuthRequest = url?.startsWith("/api/v1/auth/") || url === PUSH_REMOVE_PATH
        if (error.response?.status === 401 && !isAuthRequest) {
          endSession(false)
          const destination = window.location.pathname + window.location.search + window.location.hash
          window.location.href = `/auth?expired=1&returnTo=${encodeURIComponent(destination)}`
        }
        return Promise.reject(error)
      }
    )

    return () => {
      apiClient.interceptors.response.eject(responseInterceptor)
    }
  }, [endSession])

  const value = useMemo(() => ({ user, token, login, logout, updateUser }), [user, token, login, logout, updateUser])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
