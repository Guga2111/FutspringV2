import apiClient from "./client"
import type { PeladaResponse, PeladaDetail, PeladaAwards, PlayerPeladaStatsDTO } from "../types/pelada"
import type { UserResponseDTO } from "../types/auth"
import type { RankingDTO } from "../types/daily"

export interface CreatePeladaData {
  name: string
  dayOfWeek: string
  timeOfDay: string
  duration: number
  address?: string
  reference?: string
  autoCreateDailyEnabled?: boolean
  numberOfTeams?: number
  playersPerTeam?: number
}

export interface UpdatePeladaData {
  name?: string
  dayOfWeek?: string
  timeOfDay?: string
  duration?: number
  address?: string
  reference?: string
  autoCreateDailyEnabled?: boolean
}

export async function getMyPeladas(): Promise<PeladaResponse[]> {
  const response = await apiClient.get<PeladaResponse[]>("/api/v1/peladas/my")
  return response.data
}

export async function getPelada(id: number): Promise<PeladaDetail> {
  const response = await apiClient.get<PeladaDetail>(`/api/v1/peladas/${id}`)
  return response.data
}

export async function createPelada(data: CreatePeladaData): Promise<PeladaResponse> {
  const response = await apiClient.post<PeladaResponse>("/api/v1/peladas", data)
  return response.data
}

export async function updatePelada(id: number, data: UpdatePeladaData): Promise<PeladaResponse> {
  const response = await apiClient.put<PeladaResponse>(`/api/v1/peladas/${id}`, data)
  return response.data
}

export async function deletePelada(id: number): Promise<void> {
  await apiClient.delete(`/api/v1/peladas/${id}`)
}

export async function addPlayer(peladaId: number, userId: number): Promise<void> {
  await apiClient.post(`/api/v1/peladas/${peladaId}/players`, { userId })
}

export async function removePlayer(peladaId: number, userId: number): Promise<void> {
  await apiClient.delete(`/api/v1/peladas/${peladaId}/players/${userId}`)
}

export async function setAdmin(peladaId: number, userId: number, isAdmin: boolean): Promise<void> {
  await apiClient.put(`/api/v1/peladas/${peladaId}/players/${userId}/admin`, { isAdmin })
}

export async function uploadPeladaImage(peladaId: number, file: File): Promise<PeladaResponse> {
  const formData = new FormData()
  formData.append("file", file)
  const response = await apiClient.post<PeladaResponse>(`/api/v1/peladas/${peladaId}/image`, formData)
  return response.data
}

export async function searchUsers(q: string): Promise<UserResponseDTO[]> {
  const response = await apiClient.get<UserResponseDTO[]>("/api/v1/users/search", { params: { q } })
  return response.data
}

export async function getRanking(peladaId: number): Promise<RankingDTO[]> {
  const response = await apiClient.get<RankingDTO[]>(`/api/v1/peladas/${peladaId}/ranking`)
  return response.data
}

export async function getPeladaAwards(peladaId: number): Promise<PeladaAwards> {
  const r = await apiClient.get<PeladaAwards>(`/api/v1/peladas/${peladaId}/awards`)
  return r.data
}

export async function getPlayerPeladaStats(peladaId: number, userId: number): Promise<PlayerPeladaStatsDTO> {
  const r = await apiClient.get<PlayerPeladaStatsDTO>(`/api/v1/peladas/${peladaId}/members/${userId}/stats`)
  return r.data
}
