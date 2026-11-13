import { createContext } from "react"
import type { UserResponseDTO } from "@/types/auth"

export interface AuthContextValue {
  // the session user (also while restoring, from the cache); null = logged out
  user: UserResponseDTO | null
  // the boot refresh of a cached session hasn't answered yet
  restoring: boolean
  // the access token stays in memory (lib/session), never in the context or localStorage
  login: (token: string, user: UserResponseDTO) => void
  logout: () => void
  // merges an edited profile into the session user (sidebar, menus)
  updateUser: (patch: Partial<UserResponseDTO>) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
