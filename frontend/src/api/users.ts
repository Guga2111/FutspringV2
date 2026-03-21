import apiClient from "./client"
import type { StatsDTO } from "../types/stats"

export async function getUserStats(userId: number): Promise<StatsDTO> {
  const response = await apiClient.get<StatsDTO>(`/api/v1/users/${userId}/stats`)
  return response.data
}
