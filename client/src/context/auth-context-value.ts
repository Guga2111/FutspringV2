import { createContext } from "react"
import type { UserResponseDTO } from "../types/auth"

export interface AuthContextValue {
  user: UserResponseDTO | null
  token: string | null
  login: (token: string, user: UserResponseDTO) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
