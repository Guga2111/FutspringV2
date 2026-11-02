import * as React from "react"
import type { UseTourReturn } from "@ark-ui/react/tour"

// Kept apart from tour.tsx so that file only exports components (fast refresh), like sidebar-context.ts
export const TourContext = React.createContext<UseTourReturn | null>(null)

export function useTourContext(): UseTourReturn {
  const tour = React.use(TourContext)
  if (!tour) throw new Error("useTourContext must be used inside <Tour>")
  return tour
}
