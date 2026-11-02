import apiClient from "./client"
import type { TourKey, TourState } from "@/types/tour"

// The caller's guided tours already seen
export async function getTourState(): Promise<TourState> {
  const response = await apiClient.get<TourState>("/api/v1/tours")
  return response.data
}

// Idempotent; the tour no longer starts by itself, on any device
export async function markTourSeen(tour: TourKey): Promise<void> {
  await apiClient.post(`/api/v1/tours/${tour}/seen`)
}
