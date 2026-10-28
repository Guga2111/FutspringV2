import { useContext } from "react"
import { RealtimeContext } from "@/context/realtime-context-value"

export function useRealtime() {
  const ctx = useContext(RealtimeContext)
  if (!ctx) {
    throw new Error("useRealtime must be used within AppLayout")
  }
  return ctx
}
