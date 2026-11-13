import { useCallback, useEffect, useMemo, useState } from "react"
import type { ReactNode } from "react"
import type { UserResponseDTO } from "@/types/auth"
import apiClient from "@/api/client"
import { logoutSession } from "@/api/auth"
import { unsubscribePush } from "@/api/push"
import { getErrorStatus } from "@/lib/errors"
import { currentPushSubscription, setAppBadge } from "@/lib/push"
import {
  getAccessToken,
  installSessionInterceptors,
  onSessionExpired,
  onSessionRefreshed,
  refreshAccessToken,
  setAccessToken,
} from "@/lib/session"
import { AuthContext } from "./auth-context-value"

// The access token of the 7-day sessions, before the refresh cookie: no longer read, removed on boot (those users
// log in once more)
const LEGACY_TOKEN_KEY = "futspring_token"
// The session user, cached to know on boot that there is a session to restore and to paint the shell
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

function readCachedUser(): UserResponseDTO | null {
  try {
    const stored = localStorage.getItem(USER_KEY)
    return stored ? (JSON.parse(stored) as UserResponseDTO) : null
  } catch {
    return null
  }
}

installSessionInterceptors(apiClient)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponseDTO | null>(readCachedUser)
  // a cached user whose session the boot refresh is still restoring; PrivateRoute waits for it
  const [restoring, setRestoring] = useState(() => user !== null)

  const applySession = useCallback((newUser: UserResponseDTO) => {
    setUser(newUser)
    localStorage.setItem(USER_KEY, JSON.stringify(newUser))
  }, [])

  const login = useCallback(
    (newToken: string, newUser: UserResponseDTO) => {
      setAccessToken(newToken)
      applySession(newUser)
    },
    [applySession],
  )

  const updateUser = useCallback((patch: Partial<UserResponseDTO>) => {
    setUser((current) => {
      if (!current) return current
      const next = { ...current, ...patch }
      localStorage.setItem(USER_KEY, JSON.stringify(next))
      return next
    })
  }, [])

  // `sessionValid`: false when the server already ended it (a 401), so neither the push removal nor the logout
  // would be accepted or needed
  const endSession = useCallback((sessionValid: boolean) => {
    const closing = getAccessToken()
    void releasePushDevice(sessionValid ? closing : null)
    if (sessionValid) void logoutSession().catch(() => undefined)
    setAccessToken(null)
    setUser(null)
    setRestoring(false)
    localStorage.removeItem(USER_KEY)
  }, [])

  const logout = useCallback(() => endSession(true), [endSession])

  // Every refresh (boot, a 401, the STOMP reconnect) updates the state; an ended session goes to the login,
  // keeping the full destination
  useEffect(() => {
    const stopRefreshed = onSessionRefreshed((session) => applySession(session.user))
    const stopExpired = onSessionExpired(() => {
      endSession(false)
      const destination = window.location.pathname + window.location.search + window.location.hash
      window.location.href = `/auth?expired=1&returnTo=${encodeURIComponent(destination)}`
    })
    return () => {
      stopRefreshed()
      stopExpired()
    }
  }, [applySession, endSession])

  // Boot: the cookie gives a new access token. 401 → no session (PrivateRoute sends to the login); any other
  // failure (offline PWA, the server can't renew now) keeps the cached user: the screens show their error states
  // and the next 401 tries again
  useEffect(() => {
    localStorage.removeItem(LEGACY_TOKEN_KEY)
    if (!restoring) return
    let active = true
    refreshAccessToken()
      .catch((error: unknown) => {
        if (active && getErrorStatus(error) === 401) endSession(false)
      })
      .finally(() => {
        if (active) setRestoring(false)
      })
    return () => {
      active = false
    }
  }, [restoring, endSession])

  const value = useMemo(
    () => ({ user, restoring, login, logout, updateUser }),
    [user, restoring, login, logout, updateUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
