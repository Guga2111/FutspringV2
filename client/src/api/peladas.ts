import apiClient from "./client"
import type { PeladaResponse, PeladaDetail, PeladaMember, PeladaAwards, PeladaAttendanceDTO, PlayerPeladaHistoryDTO, PlayerPeladaStatsDTO } from "@/types/pelada"
import type { PublicUser } from "@/types/user"
import type { RankingDTO } from "@/types/daily"

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
  // IANA zone; the backend defaults to America/Sao_Paulo
  timeZone?: string
  maxMensalistas?: number
}

export interface UpdatePeladaData {
  name?: string
  dayOfWeek?: string
  timeOfDay?: string
  duration?: number
  address?: string
  reference?: string
  autoCreateDailyEnabled?: boolean
  timeZone?: string
  // the new capacity also applies to the sessions already scheduled; nobody is removed
  numberOfTeams?: number
  playersPerTeam?: number
  maxMensalistas?: number
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

// admin only; returns the member with the new stars
export async function setPlayerStars(peladaId: number, userId: number, stars: number): Promise<PeladaMember> {
  const response = await apiClient.put<PeladaMember>(`/api/v1/peladas/${peladaId}/players/${userId}/stars`, { stars })
  return response.data
}

// admin only; 409 when the mensalista limit is full. Returns the member with the new type
export async function setMensalista(peladaId: number, userId: number, isMensalista: boolean): Promise<PeladaMember> {
  const response = await apiClient.put<PeladaMember>(`/api/v1/peladas/${peladaId}/players/${userId}/mensalista`, { isMensalista })
  return response.data
}

// Bulk member actions (admin only): atomic, one invalid target (not a member 404, the creator 403, the mensalista
// limit 409) changes nobody. They return the targets as they are now
export async function setAdmins(peladaId: number, userIds: number[], isAdmin: boolean): Promise<PeladaMember[]> {
  const response = await apiClient.put<PeladaMember[]>(`/api/v1/peladas/${peladaId}/players/admin`, { userIds, isAdmin })
  return response.data
}

export async function setMensalistas(peladaId: number, userIds: number[], isMensalista: boolean): Promise<PeladaMember[]> {
  const response = await apiClient.put<PeladaMember[]>(`/api/v1/peladas/${peladaId}/players/mensalista`, {
    userIds,
    isMensalista,
  })
  return response.data
}

export async function setPlayersStars(peladaId: number, userIds: number[], stars: number): Promise<PeladaMember[]> {
  const response = await apiClient.put<PeladaMember[]>(`/api/v1/peladas/${peladaId}/players/stars`, { userIds, stars })
  return response.data
}

// POST, not DELETE with a body
export async function removePlayers(peladaId: number, userIds: number[]): Promise<void> {
  await apiClient.post(`/api/v1/peladas/${peladaId}/players/remove`, { userIds })
}

export async function uploadPeladaImage(peladaId: number, file: File): Promise<PeladaResponse> {
  const formData = new FormData()
  formData.append("file", file)
  const response = await apiClient.post<PeladaResponse>(`/api/v1/peladas/${peladaId}/image`, formData)
  return response.data
}

// Back to the gradient with initials
export async function removePeladaImage(peladaId: number): Promise<PeladaResponse> {
  const response = await apiClient.delete<PeladaResponse>(`/api/v1/peladas/${peladaId}/image`)
  return response.data
}

// Creator only; userId must be an admin of the pelada. The old creator stays admin
export async function transferPelada(peladaId: number, userId: number): Promise<PeladaResponse> {
  const response = await apiClient.put<PeladaResponse>(`/api/v1/peladas/${peladaId}/creator`, { userId })
  return response.data
}

// q needs at least 3 characters (400 otherwise); matches username or email, returns public fields only
export async function searchUsers(q: string): Promise<PublicUser[]> {
  const response = await apiClient.get<PublicUser[]>("/api/v1/users/search", { params: { q } })
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

// limit = last N sessions (1–100); null = every session
export async function getPlayerPeladaHistory(peladaId: number, userId: number, limit: number | null): Promise<PlayerPeladaHistoryDTO> {
  const r = await apiClient.get<PlayerPeladaHistoryDTO>(`/api/v1/peladas/${peladaId}/members/${userId}/history`, {
    params: limit !== null ? { limit } : undefined,
  })
  return r.data
}

// Attendance ranking of the last 6 months (finalized sessions only)
export async function getPeladaAttendance(peladaId: number): Promise<PeladaAttendanceDTO> {
  const r = await apiClient.get<PeladaAttendanceDTO>(`/api/v1/peladas/${peladaId}/attendance`)
  return r.data
}
