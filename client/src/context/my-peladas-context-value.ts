import { createContext } from "react"
import type { NextDaily, PeladaResponse } from "@/types/pelada"

// The user's peladas, loaded once by AppLayout for the sidebar and the Home page
export interface MyPeladasContextValue {
  peladas: PeladaResponse[]
  loading: boolean
  error: boolean
  reload: () => void
  // merge a mutation's result (confirm / unconfirm) into a pelada's next session
  updateNextDaily: (peladaId: number, patch: Partial<NextDaily>) => void
  openCreatePelada: () => void
}

export const MyPeladasContext = createContext<MyPeladasContextValue | null>(null)
