import apiClient from "./client"
import { WITH_SESSION_COOKIE } from "./auth"
import type { StatsDTO, UserStatsTimelineDTO, UserMatchHistoryDTO } from "@/types/stats"
import type { PeladaResponse } from "@/types/pelada"
import type { AuthResponse } from "@/types/auth"
import type { DeletionBlocker, ProfileDTO } from "@/types/user"

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
  data: { username?: string; position?: string },
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

// Peladas that the current user and userId both belong to
export async function getUserPeladas(userId: number): Promise<PeladaResponse[]> {
  const response = await apiClient.get<PeladaResponse[]>(`/api/v1/users/${userId}/peladas`)
  return response.data
}

export interface ChangePasswordData {
  currentPassword: string
  newPassword: string
}

// Answers a token of the new version and a new refresh cookie: the other sessions are logged out
export async function changePassword(data: ChangePasswordData): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/v1/users/me/password", data, WITH_SESSION_COOKIE)
  return response.data
}

export async function getDeletionBlockers(): Promise<DeletionBlocker[]> {
  const response = await apiClient.get<DeletionBlocker[]>("/api/v1/users/me/deletion-blockers")
  return response.data
}

// POST: the password goes in the body
export async function deleteAccount(password: string): Promise<void> {
  await apiClient.post("/api/v1/users/me/delete", { password })
}

// LGPD access and portability: the caller's data as a JSON file. The client only saves it, so it stays a Blob
// (no type in src/types)
export async function exportMyData(): Promise<Blob> {
  const response = await apiClient.get<Blob>("/api/v1/users/me/export", { responseType: "blob" })
  return response.data
}
