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
  status: string
  confirmedPlayerCount: number
  isFinished: boolean
}

export interface DailyDetail {
  id: number
  dailyDate: string
  dailyTime: string
  status: string
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
