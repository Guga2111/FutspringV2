import apiClient from "./client"
import type { StatsDTO, UserStatsTimelineDTO, UserMatchHistoryDTO } from "../types/stats"
import type { ProfileDTO } from "../types/user"

export async function getUserStats(userId: number): Promise<StatsDTO> {
  const response = await apiClient.get<StatsDTO>(`/api/v1/users/${userId}/stats`)
  return response.data
}

export async function getUser(userId: number): Promise<ProfileDTO> {
  const response = await apiClient.get<ProfileDTO>(`/api/v1/users/${userId}`)
  return response.data
}

export async function updateUser(
  userId: number,
  data: { username?: string; position?: string; stars?: number },
): Promise<ProfileDTO> {
  const response = await apiClient.put<ProfileDTO>(`/api/v1/users/${userId}`, data)
  return response.data
}

export async function uploadUserImage(userId: number, file: File): Promise<ProfileDTO> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await apiClient.post<ProfileDTO>(`/api/v1/users/${userId}/image`, formData)
  return response.data
}

export async function uploadBackgroundImage(userId: number, file: File): Promise<ProfileDTO> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await apiClient.post<ProfileDTO>(
    `/api/v1/users/${userId}/background-image`,
    formData,
  )
  return response.data
}

export async function getUserStatsTimeline(
  userId: number,
  from: string,
  to: string,
): Promise<UserStatsTimelineDTO> {
  const response = await apiClient.get<UserStatsTimelineDTO>(
    `/api/v1/users/${userId}/stats/timeline`,
    { params: { from, to } },
  )
  return response.data
}

export async function getUserMatchHistory(userId: number): Promise<UserMatchHistoryDTO> {
  const response = await apiClient.get<UserMatchHistoryDTO>(
    `/api/v1/users/${userId}/stats/matches`,
  )
  return response.data
}
