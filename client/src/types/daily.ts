// mirrors the backend DailyStatus enum
export type DailyStatus = "SCHEDULED" | "CONFIRMED" | "IN_COURSE" | "FINISHED" | "CANCELED"

export const dailyStatusLabel: Record<DailyStatus, string> = {
  SCHEDULED: "Agendada",
  CONFIRMED: "Confirmada",
  IN_COURSE: "Em andamento",
  FINISHED: "Finalizada",
  CANCELED: "Cancelada",
}

// Badge background/text and the status dot (tokens in index.css)
export const dailyStatusStyle: Record<DailyStatus, { badge: string; dot: string }> = {
  SCHEDULED: { badge: "bg-status-scheduled text-status-scheduled-foreground", dot: "bg-status-scheduled-dot" },
  CONFIRMED: { badge: "bg-success-muted text-success-soft", dot: "bg-success-strong" },
  IN_COURSE: { badge: "bg-status-live text-status-live-foreground", dot: "bg-status-live-dot" },
  FINISHED: { badge: "bg-secondary text-secondary-foreground", dot: "bg-muted-foreground" },
  CANCELED: { badge: "bg-destructive-muted text-destructive-soft", dot: "bg-destructive" },
}

// Attendance and teams can only change before the session starts (backend: DailyStatus.LOCKED)
export function isDailyOpen(status: DailyStatus): boolean {
  return status === "SCHEDULED" || status === "CONFIRMED"
}

export interface PlayerDTO {
  id: number
  username: string
  image: string | null
  stars: number
  position: string | null
}

export interface TeamDTO {
  id: number
  name: string
  totalStars: number
  averageStars: number
  color: string | null
  players: PlayerDTO[]
}

export interface MatchDTO {
  id: number
  team1Id: number
  team1Name: string
  team2Id: number
  team2Name: string
  team1Score: number | null
  team2Score: number | null
  winnerId: number | null
  playerStats: { userId: number; goals: number; assists: number }[]
}

export interface UserDailyStatsDTO {
  userId: number
  username: string
  goals: number
  assists: number
  matchesPlayed: number
  wins: number
}

export interface LeagueTableEntryDTO {
  teamId: number
  teamName: string
  position: number
  wins: number
  draws: number
  losses: number
  goalsFor: number
  goalsAgainst: number
  goalDiff: number
  points: number
}

export interface AwardDTO {
  puskasWinnerIds: number[]
  puskasWinnerNames: string[]
  wiltballWinnerIds: number[]
  wiltballWinnerNames: string[]
  artilheiroWinnerIds: number[]
  artilheiroWinnerNames: string[]
  garcomWinnerIds: number[]
  garcomWinnerNames: string[]
}

export interface RankingDTO {
  userId: number
  username: string
  userImage: string | null
  goals: number
  assists: number
  matchesPlayed: number
  wins: number
}

export interface DailyListItem {
  id: number
  dailyDate: string
  dailyTime: string
  status: DailyStatus
  confirmedPlayerCount: number
  isFinished: boolean
}

export interface DailyDetail {
  id: number
  dailyDate: string
  dailyTime: string
  status: DailyStatus
  isFinished: boolean
  championImage: string | null
  confirmedPlayers: PlayerDTO[]
  teams: TeamDTO[]
  matches: MatchDTO[]
  playerStats: UserDailyStatsDTO[]
  leagueTable: LeagueTableEntryDTO[]
  award: AwardDTO | null
  peladaId: number
  peladaName: string
  numberOfTeams: number
  playersPerTeam: number
  isAdmin: boolean
  peladaMembers: PlayerDTO[] | null
}
