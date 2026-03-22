export interface StatsDTO {
  userId: number
  username: string
  goals: number
  assists: number
  matchesPlayed: number
  wins: number
  puskasDates: string[]
}

export interface TimelinePoint {
  date: string
  goals: number
  assists: number
  wins: number
  matchesPlayed: number
}

export interface UserStatsTimelineDTO {
  points: TimelinePoint[]
}

export interface MatchHistoryRow {
  dailyId: number
  date: string
  peladaId: number
  peladaName: string
  goals: number
  assists: number
  matchesPlayed: number
  wins: number
  result: string
}

export interface UserMatchHistoryDTO {
  rows: MatchHistoryRow[]
}
