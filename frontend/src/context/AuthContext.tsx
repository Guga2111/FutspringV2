import { createContext, useCallback, useEffect, useMemo, useState } from "react"
import type { ReactNode } from "react"
import type { UserResponseDTO } from "../types/auth"
import apiClient from "../api/client"

const TOKEN_KEY = "futspring_token"
const USER_KEY = "futspring_user"

interface AuthContextValue {
  user: UserResponseDTO | null
  token: string | null
  login: (token: string, user: UserResponseDTO) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

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

  // Set up Axios interceptors inside the provider so they have access to logout
  useEffect(() => {
    const requestInterceptor = apiClient.interceptors.request.use((config) => {
      const storedToken = localStorage.getItem(TOKEN_KEY)
      if (storedToken) {
        config.headers.Authorization = `Bearer ${storedToken}`
      }
      return config
    })

    const responseInterceptor = apiClient.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          logout()
          window.location.href = "/auth"
        }
        return Promise.reject(error)
      }
    )

    return () => {
      apiClient.interceptors.request.eject(requestInterceptor)
      apiClient.interceptors.response.eject(responseInterceptor)
    }
  }, [logout])

  const value = useMemo(() => ({ user, token, login, logout }), [user, token, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
