import { useCallback, useEffect, useState } from "react"
import { getTourState, markTourSeen } from "@/api/tours"
import type { TourKey } from "@/types/tour"

// Whether the finance tour should start by itself: only once the server answered that this tour wasn't seen.
// A failed read never starts it (the "?" button still does). Marking it seen is optimistic and best effort:
// if the request fails, the worst case is the tour starting again on the next visit.
export function useFinanceTour(tour: TourKey | null) {
  const [seen, setSeen] = useState<ReadonlySet<TourKey> | null>(null)

  useEffect(() => {
    let cancelled = false
    getTourState()
      .then((state) => { if (!cancelled) setSeen(new Set(state.seen)) })
      .catch(() => { /* stays null: no automatic start */ })
    return () => { cancelled = true }
  }, [])

  const markSeen = useCallback((key: TourKey) => {
    setSeen((prev) => (prev?.has(key) ? prev : new Set([...(prev ?? []), key])))
    markTourSeen(key).catch(() => { /* best effort, see above */ })
  }, [])

  return { shouldAutoStart: tour !== null && seen !== null && !seen.has(tour), markSeen }
}
