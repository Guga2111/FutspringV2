import { useContext } from "react"
import { MyPeladasContext } from "@/context/my-peladas-context-value"

export function useMyPeladasContext() {
  const ctx = useContext(MyPeladasContext)
  if (!ctx) {
    throw new Error("useMyPeladasContext must be used within AppLayout")
  }
  return ctx
}
