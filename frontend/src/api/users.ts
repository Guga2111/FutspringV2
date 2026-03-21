import apiClient from "./client"
import type { StatsDTO } from "../types/stats"
import type { ProfileDTO } from "../types/user"

export async function getUserStats(userId: number): Promise<StatsDTO> {
  const response = await apiClient.get<StatsDTO>(`/api/v1/users/${userId}/stats`)
  return response.data
}

export async function getUser(userId: number): Promise<ProfileDTO> {
  const response = await apiClient.get<ProfileDTO>(`/api/v1/users/${userId}`)
  return response.data
}
