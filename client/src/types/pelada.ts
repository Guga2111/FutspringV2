import type { AttendanceBump, DailyStatus } from "@/types/daily"

export interface PeladaMember {
  id: number
  username: string
  image: string | null
  // the member's stars in this pelada, set by its admins
  stars: number
  position: string | null
  isAdmin: boolean
  // mensalista (priority on attendance) or diarista
  isMensalista: boolean
}

export interface PeladaResponse {
  id: number
  name: string
  dayOfWeek: string
  timeOfDay: string
  duration: number
  address: string | null
  reference: string | null
  image: string | null
  autoCreateDailyEnabled: boolean
  memberCount: number
  numberOfTeams: number
  playersPerTeam: number
  // IANA zone the sessions' date and time are in (America/Sao_Paulo)
  timeZone: string
  // whether the caller administers the pelada
  isAdmin: boolean
  // next SCHEDULED/CONFIRMED session from today; only filled by /peladas/my and /users/{id}/peladas
  nextDaily: NextDaily | null
}

// mirrors PeladaResponseDTO.NextDailyDTO
export interface NextDaily {
  id: number
  date: string
  time: string
  status: DailyStatus
  confirmedCount: number
  // numberOfTeams × playersPerTeam
  capacity: number
  // whether the caller confirmed attendance
  isConfirmed: boolean
  // ISO instant of the session start
  startsAt: string
  // ISO instant from when the caller may confirm themselves; null for mensalistas (the server re-checks it)
  confirmOpensAt: string | null
  // the caller's bump in this session not dismissed yet
  bump: AttendanceBump | null
}

// mirrors PeladaDetailResponseDTO: no caller flags (isAdmin comes from members[]) and no next session
export interface PeladaDetail extends Omit<PeladaResponse, "isAdmin" | "nextDaily"> {
  creatorId: number | null
  // how many members can be mensalistas (0 ≤ max ≤ numberOfTeams × playersPerTeam)
  maxMensalistas: number
  members: PeladaMember[]
}

export interface AwardWinner {
  userId: number
  username: string
  userImage: string | null
  count: number
}

export interface AwardCategory {
  type: string
  name: string
  description: string
  topWinners: AwardWinner[]
}

export interface PeladaAwards {
  totalCategories: number
  totalAwardsDistributed: number
  categories: AwardCategory[]
}

export interface PlayerPeladaHistoryRow {
  dailyId: number
  date: string
  goals: number
  assists: number
  matchesPlayed: number
  wins: number
  wonSession: boolean
}

// mirrors PlayerPeladaHistoryDTO (rows newest first, at most `limit`)
export interface PlayerPeladaHistoryDTO {
  userId: number
  totalSessions: number
  rows: PlayerPeladaHistoryRow[]
}

export interface PeladaAttendanceRow {
  userId: number
  username: string
  userImage: string | null
  sessionsAttended: number
  // sessionsAttended / totalSessions (0–1), 0 when there are no sessions
  rate: number
  // the member's current type, not their type at each session of the period
  isMensalista: boolean
}

// mirrors PeladaAttendanceDTO (last 6 months, rows best rate first)
export interface PeladaAttendanceDTO {
  from: string
  to: string
  totalSessions: number
  rows: PeladaAttendanceRow[]
}

export interface PlayerPeladaStatsDTO {
  userId: number
  // the player's stars in this pelada
  stars: number
  goals: number
  assists: number
  matchesPlayed: number
  wins: number
  matchWins: number
  artilheiroWins: number
  garcomWins: number
  puskasWins: number
  bolaMurchaWins: number
}
