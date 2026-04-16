import apiClient from "./client"
import type { DailyListItem, DailyDetail, MatchDTO, TeamDTO } from "../types/daily"

export interface CreateDailyData {
  dailyDate: string
  dailyTime: string
}

export interface PlayerStatInput {
  userId: number
  goals: number
  assists: number
}

export interface MatchResultInput {
  matchId?: number | null
  team1Id: number
  team2Id: number
  team1Score: number
  team2Score: number
  playerStats: PlayerStatInput[]
}

export async function getDailiesForPelada(peladaId: number): Promise<DailyListItem[]> {
  const response = await apiClient.get<DailyListItem[]>(`/api/v1/peladas/${peladaId}/dailies`)
  return response.data
}

export async function getDailyDetail(id: number): Promise<DailyDetail> {
  const response = await apiClient.get<DailyDetail>(`/api/v1/dailies/${id}`)
  return response.data
}

export async function createDaily(peladaId: number, data: CreateDailyData): Promise<DailyListItem> {
  const response = await apiClient.post<DailyListItem>(`/api/v1/peladas/${peladaId}/dailies`, data)
  return response.data
}

export async function confirmAttendance(id: number): Promise<DailyListItem> {
  const response = await apiClient.post<DailyListItem>(`/api/v1/dailies/${id}/confirm`)
  return response.data
}

export async function disconfirmAttendance(id: number): Promise<DailyListItem> {
  const response = await apiClient.delete<DailyListItem>(`/api/v1/dailies/${id}/confirm`)
  return response.data
}

export async function adminConfirmAttendance(id: number, userId: number): Promise<DailyListItem> {
  const response = await apiClient.post<DailyListItem>(`/api/v1/dailies/${id}/confirm/${userId}`)
  return response.data
}

export async function adminDisconfirmAttendance(id: number, userId: number): Promise<DailyListItem> {
  const response = await apiClient.delete<DailyListItem>(`/api/v1/dailies/${id}/confirm/${userId}`)
  return response.data
}

export async function sortTeams(id: number): Promise<DailyDetail> {
  const response = await apiClient.post<DailyDetail>(`/api/v1/dailies/${id}/sort-teams`)
  return response.data
}

export async function swapPlayers(id: number, player1Id: number, player2Id: number): Promise<DailyDetail> {
  const response = await apiClient.put<DailyDetail>(`/api/v1/dailies/${id}/teams/swap`, { player1Id, player2Id })
  return response.data
}

export async function updateDailyStatus(id: number, status: string): Promise<DailyListItem> {
  const response = await apiClient.put<DailyListItem>(`/api/v1/dailies/${id}/status`, { status })
  return response.data
}

export async function submitResults(id: number, results: MatchResultInput[]): Promise<MatchDTO[]> {
  const response = await apiClient.post<MatchDTO[]>(`/api/v1/dailies/${id}/results`, results)
  return response.data
}

export async function finalizeDaily(id: number, puskasWinnerIds: number[], wiltballWinnerIds: number[]): Promise<DailyDetail> {
  const response = await apiClient.post<DailyDetail>(`/api/v1/dailies/${id}/finalize`, {
    puskasWinnerIds,
    wiltballWinnerIds,
  })
  return response.data
}

export async function updateTeamName(dailyId: number, teamId: number, name: string): Promise<TeamDTO> {
  const response = await apiClient.patch<TeamDTO>(`/api/v1/dailies/${dailyId}/teams/${teamId}/name`, { name })
  return response.data
}

export async function updateTeamColor(dailyId: number, teamId: number, color: string): Promise<TeamDTO> {
  const response = await apiClient.patch<TeamDTO>(`/api/v1/dailies/${dailyId}/teams/${teamId}/color`, { color })
  return response.data
}

export interface PopulatePlayerInput {
  userId: number
  totalGoals: number
  totalAssists: number
}

export interface PopulateTeamInput {
  colorName: string
  colorHex: string
  players: PopulatePlayerInput[]
}

export interface PopulateMatchInput {
  team1ColorName: string
  team1Score: number
  team2ColorName: string
  team2Score: number
}

export interface PopulateDailyInput {
  teams: PopulateTeamInput[]
  matches: PopulateMatchInput[]
}

export async function populateFromMessage(id: number, data: PopulateDailyInput): Promise<DailyDetail> {
  const response = await apiClient.post<DailyDetail>(`/api/v1/dailies/${id}/populate`, data)
  return response.data
}

export async function deleteDaily(id: number): Promise<void> {
  await apiClient.delete(`/api/v1/dailies/${id}`)
}

export async function uploadChampionImage(id: number, file: File): Promise<DailyListItem> {
  const formData = new FormData()
  formData.append("file", file)
  const response = await apiClient.put<DailyListItem>(`/api/v1/dailies/${id}/champion-image`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  })
  return response.data
}
