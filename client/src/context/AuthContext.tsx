import { useCallback, useEffect, useMemo, useState } from "react"
import type { ReactNode } from "react"
import type { UserResponseDTO } from "@/types/auth"
import apiClient from "@/api/client"
import { AuthContext } from "./auth-context-value"

const TOKEN_KEY = "futspring_token"
const USER_KEY = "futspring_user"

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

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }, [])

  // The 401 interceptor lives inside the provider so it has access to logout
  useEffect(() => {
    const responseInterceptor = apiClient.interceptors.response.use(
      (response) => response,
      (error) => {
        // A 401 from login/register is a wrong password or similar, shown by the form, not an expired session
        const isAuthRequest = error.config?.url?.startsWith("/api/v1/auth/")
        if (error.response?.status === 401 && !isAuthRequest) {
          logout()
          window.location.href = "/auth"
        }
        return Promise.reject(error)
      }
    )

    return () => {
      apiClient.interceptors.response.eject(responseInterceptor)
    }
  }, [logout])

  const value = useMemo(() => ({ user, token, login, logout }), [user, token, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
