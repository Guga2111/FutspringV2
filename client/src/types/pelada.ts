export interface PeladaMember {
  id: number
  username: string
  image: string | null
  stars: number
  position: string | null
  isAdmin: boolean
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
  // next SCHEDULED/CONFIRMED session from today; only sent by /peladas/my and /users/{id}/peladas
  nextDailyDate?: string | null
}

export interface PeladaDetail extends PeladaResponse {
  creatorId: number | null
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

export interface PlayerPeladaStatsDTO {
  userId: number
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
