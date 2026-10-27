import { useEffect, useState } from "react"

// setTimeout fires at once above this delay (~24.8 days); longer waits re-arm on each tick
const MAX_DELAY_MS = 2_147_483_647

/**
 * The current time, re-read once when `iso` is reached, so UI that depends on an instant (the diarista
 * confirmation window) updates itself at that moment. A single timeout, no polling.
 */
export function useNowUntil(iso: string | null): Date {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    if (!iso) return
    const delay = new Date(iso).getTime() - now.getTime()
    if (delay <= 0) return
    const id = window.setTimeout(() => setNow(new Date()), Math.min(delay + 50, MAX_DELAY_MS))
    return () => window.clearTimeout(id)
  }, [iso, now])
  return now
}
