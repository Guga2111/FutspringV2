// Typed API fixtures for the visual tests. Types come from src/types, so a DTO change breaks `tsc`
// here instead of silently rendering a stale screen. Dates are relative to NOW (the frozen clock).
import type { UserResponseDTO } from "../src/types/auth"
import type { ProfileDTO, PublicUser } from "../src/types/user"
import type { PeladaAwards, PeladaAttendanceDTO, PeladaDetail, PeladaMember, PeladaResponse } from "../src/types/pelada"
import type { DailyDetail, DailyListItem, PlayerDTO, RankingDTO, TeamDTO } from "../src/types/daily"
import type { StatsDTO, UserMatchHistoryDTO, UserStatsTimelineDTO } from "../src/types/stats"
import type { NotificationPreference, NotificationResponse } from "../src/types/notification"
import type { Invitation } from "../src/types/invitation"
import type { FinanceDetail, FinanceOverview } from "../src/types/finance"

// Wednesday 2026-11-04 15:00 in São Paulo
export const NOW = new Date("2026-11-04T18:00:00.000Z")

export const PELADA_ID = 1
export const SCHEDULED_ID = 203
export const LIVE_ID = 202
export const FINISHED_ID = 201

const NAMES = ["Leal", "Bruno", "Caio", "Diego", "Edu", "Fabio", "Gabriel", "Hugo", "Igor", "João", "Kaique", "Lucas"]
const POSITIONS = ["GOLEIRO", "ZAGUEIRO", "MEIO", "ATACANTE"]

export const me: UserResponseDTO = {
  id: 1,
  email: "leal@futspring.com",
  username: "Leal",
  image: null,
  backgroundImage: null,
  position: "MEIO",
}

export const members: PeladaMember[] = NAMES.map((username, i) => ({
  id: i + 1,
  username,
  image: null,
  stars: (i % 5) + 1,
  position: POSITIONS[i % 4],
  isAdmin: i < 2,
  isMensalista: i % 3 === 0,
}))

const player = (m: PeladaMember): PlayerDTO => ({
  id: m.id,
  username: m.username,
  image: null,
  stars: m.stars,
  position: m.position,
})
const publicUser = (m: PeladaMember): PublicUser => ({ id: m.id, username: m.username, image: null, position: m.position })

const nextDaily = {
  id: SCHEDULED_ID,
  date: "2026-11-05",
  time: "21:00",
  status: "SCHEDULED" as const,
  confirmedCount: 7,
  capacity: 10,
  isConfirmed: false,
  startsAt: "2026-11-06T00:00:00.000Z",
  confirmOpensAt: null,
  bump: null,
}

export const myPeladas: PeladaResponse[] = [
  {
    id: PELADA_ID,
    name: "Pelada do Fut",
    dayOfWeek: "THURSDAY",
    timeOfDay: "21:00",
    duration: 90,
    address: "Arena Society, Rua das Flores 120",
    reference: "Portão azul",
    image: null,
    autoCreateDailyEnabled: true,
    memberCount: members.length,
    numberOfTeams: 2,
    playersPerTeam: 5,
    timeZone: "America/Sao_Paulo",
    isAdmin: true,
    nextDaily,
  },
  {
    id: 2,
    name: "Fut de Domingo",
    dayOfWeek: "SUNDAY",
    timeOfDay: "09:00",
    duration: 60,
    address: "Quadra do Clube",
    reference: null,
    image: null,
    autoCreateDailyEnabled: false,
    memberCount: 8,
    numberOfTeams: 2,
    playersPerTeam: 4,
    timeZone: "America/Sao_Paulo",
    isAdmin: false,
    nextDaily: null,
  },
]

// the detail has no caller flags; the extra summary fields are ignored by the client
export const peladaDetail: PeladaDetail = { ...myPeladas[0], creatorId: 1, maxMensalistas: 4, members }

export const dailies: DailyListItem[] = [
  {
    id: SCHEDULED_ID,
    dailyDate: "2026-11-05",
    dailyTime: "21:00",
    status: "SCHEDULED",
    confirmedPlayerCount: 7,
    isFinished: false,
    teamCount: 0,
    matchCount: 0,
    championImage: null,
    isConfirmed: false,
    capacity: 10,
    startsAt: "2026-11-06T00:00:00.000Z",
    confirmOpensAt: null,
  },
  ...[FINISHED_ID, 200, 199, 198].map((id, i): DailyListItem => {
    const day = 29 - i * 7
    const date = day > 0 ? `2026-10-${String(day).padStart(2, "0")}` : `2026-09-${30 + day}`
    return {
      id,
      dailyDate: date,
      dailyTime: "21:00",
      status: "FINISHED",
      confirmedPlayerCount: 10,
      isFinished: true,
      teamCount: 2,
      matchCount: 4,
      championImage: null,
      isConfirmed: true,
      capacity: 10,
      startsAt: `${date}T00:00:00.000Z`,
      confirmOpensAt: null,
    }
  }),
]

export const ranking: RankingDTO[] = members.map((m, i) => ({
  userId: m.id,
  username: m.username,
  userImage: null,
  goals: 24 - i * 2,
  assists: 15 - i,
  matchesPlayed: 30 - i,
  wins: 18 - i,
}))

export const awards: PeladaAwards = {
  totalCategories: 4,
  totalAwardsDistributed: 28,
  categories: [
    ["ARTILHEIRO", "Artilheiro", "Mais gols na sessão"],
    ["GARCOM", "Garçom", "Mais assistências na sessão"],
    ["PUSKAS", "Puskás", "Gol mais bonito"],
    ["WILTBALL", "Bola Murcha", "Pior atuação"],
  ].map(([type, name, description], c) => ({
    type,
    name,
    description,
    topWinners: members.slice(c, c + 3).map((m, i) => ({ userId: m.id, username: m.username, userImage: null, count: 5 - i })),
  })),
}

export const attendance: PeladaAttendanceDTO = {
  from: "2026-05-04",
  to: "2026-11-04",
  totalSessions: 24,
  rows: members.map((m, i) => ({
    userId: m.id,
    username: m.username,
    userImage: null,
    sessionsAttended: 24 - i * 2,
    rate: (24 - i * 2) / 24,
    isMensalista: m.isMensalista,
  })),
}

const teamColors = ["#3b82f6", "#f8fafc"]
const teams: TeamDTO[] = [0, 1].map((t) => {
  const players = members.slice(t * 5, t * 5 + 5)
  const total = players.reduce((sum, p) => sum + p.stars, 0)
  return {
    id: 300 + t,
    name: t === 0 ? "Azul" : "Branco",
    totalStars: total,
    averageStars: total / 5,
    color: teamColors[t],
    players: players.map(player),
  }
})

const matches = [
  [2, 1],
  [0, 0],
  [1, 3],
  [2, 2],
].map(([s1, s2], i) => ({
  id: 400 + i,
  team1Id: 300,
  team1Name: "Azul",
  team2Id: 301,
  team2Name: "Branco",
  team1Score: s1,
  team2Score: s2,
  winnerId: s1 > s2 ? 300 : s2 > s1 ? 301 : null,
  playerStats: [
    { userId: 1, goals: s1 > 0 ? 1 : 0, assists: 1 },
    { userId: 6, goals: s2 > 0 ? 1 : 0, assists: 0 },
  ],
}))

const baseDaily = {
  championImage: null,
  peladaId: PELADA_ID,
  peladaName: "Pelada do Fut",
  numberOfTeams: 2,
  playersPerTeam: 5,
  isAdmin: true,
  peladaMembers: members.map((m) => ({ ...player(m), isMensalista: m.isMensalista })),
  capacity: 10,
  timeZone: "America/Sao_Paulo",
  confirmOpensAt: null,
  myBump: null,
}

const confirmed = (count: number): PlayerDTO[] =>
  members.slice(0, count).map((m, i) => ({
    ...player(m),
    isMensalista: m.isMensalista,
    confirmedAt: new Date(NOW.getTime() - (count - i) * 3_600_000).toISOString(),
  }))

export const scheduledDaily: DailyDetail = {
  ...baseDaily,
  id: SCHEDULED_ID,
  dailyDate: "2026-11-05",
  dailyTime: "21:00",
  status: "SCHEDULED",
  isFinished: false,
  confirmedPlayers: confirmed(7),
  teams: [],
  matches: [],
  playerStats: [],
  leagueTable: [],
  award: null,
  startsAt: "2026-11-06T00:00:00.000Z",
  bumps: [{ user: publicUser(members[10]), bumpedBy: publicUser(members[3]), bumpedAt: "2026-11-04T12:00:00.000Z" }],
}

const leagueTable = [
  { teamId: 300, teamName: "Azul", position: 1, wins: 1, draws: 2, losses: 1, goalsFor: 5, goalsAgainst: 6, goalDiff: -1, points: 5 },
  { teamId: 301, teamName: "Branco", position: 2, wins: 1, draws: 2, losses: 1, goalsFor: 6, goalsAgainst: 5, goalDiff: 1, points: 5 },
]

const playerStats = members.slice(0, 10).map((m, i) => ({
  userId: m.id,
  username: m.username,
  goals: Math.max(0, 3 - i),
  assists: i % 3,
  matchesPlayed: 4,
  wins: i < 5 ? 1 : 1,
}))

export const liveDaily: DailyDetail = {
  ...baseDaily,
  id: LIVE_ID,
  dailyDate: "2026-11-04",
  dailyTime: "14:00",
  status: "IN_COURSE",
  isFinished: false,
  confirmedPlayers: confirmed(10),
  teams,
  matches: matches.slice(0, 2),
  playerStats: [],
  leagueTable,
  award: null,
  startsAt: "2026-11-04T17:00:00.000Z",
  bumps: [],
}

export const finishedDaily: DailyDetail = {
  ...baseDaily,
  id: FINISHED_ID,
  dailyDate: "2026-10-29",
  dailyTime: "21:00",
  status: "FINISHED",
  isFinished: true,
  confirmedPlayers: confirmed(10),
  teams,
  matches,
  playerStats,
  leagueTable,
  award: {
    puskasWinnerIds: [3],
    puskasWinnerNames: ["Caio"],
    wiltballWinnerIds: [9],
    wiltballWinnerNames: ["Igor"],
    artilheiroWinnerIds: [1],
    artilheiroWinnerNames: ["Leal"],
    garcomWinnerIds: [2],
    garcomWinnerNames: ["Bruno"],
  },
  startsAt: "2026-10-30T00:00:00.000Z",
  bumps: [],
}

export const profile: ProfileDTO = { ...me, stars: 4 }

export const stats: StatsDTO = {
  userId: 1,
  username: "Leal",
  goals: 42,
  assists: 27,
  matchesPlayed: 96,
  wins: 51,
  sessionsPlayed: 24,
  matchWins: 51,
  wiltballWins: 1,
  artilheiroWins: 6,
  garcomWins: 4,
  puskasDates: ["2026-09-10", "2026-10-15"],
}

export const timeline: UserStatsTimelineDTO = {
  points: Array.from({ length: 8 }, (_, i) => ({
    date: `2026-${String(4 + i).padStart(2, "0")}-01`,
    goals: [3, 5, 2, 6, 4, 7, 5, 8][i],
    assists: [2, 3, 1, 4, 3, 2, 5, 4][i],
    wins: [4, 6, 3, 7, 5, 6, 8, 7][i],
    matchesPlayed: [10, 12, 8, 14, 11, 12, 15, 14][i],
  })),
}

export const matchHistory: UserMatchHistoryDTO = {
  rows: dailies
    .filter((d) => d.isFinished)
    .map((d, i) => ({
      dailyId: d.id,
      date: d.dailyDate,
      peladaId: PELADA_ID,
      peladaName: "Pelada do Fut",
      goals: 3 - i,
      assists: i % 2,
      matchesPlayed: 4,
      wins: 2,
      result: i === 0 ? "CHAMPION" : "WIN",
    })),
}

export const invitations: Invitation[] = [
  {
    id: 900,
    peladaId: 3,
    pelada: { id: 3, name: "Racha da Firma", image: null, dayOfWeek: "TUESDAY", timeOfDay: "19:30" },
    recipient: publicUser(members[0]),
    sender: publicUser(members[4]),
    status: "PENDING",
    createdAt: "2026-11-03T12:00:00.000Z",
    expiresAt: "2026-11-10T12:00:00.000Z",
    respondedAt: null,
    canceledAt: null,
    isMember: false,
  },
]

export const notifications: NotificationResponse[] = [
  {
    id: 1,
    invitationId: null,
    type: "SESSION_CREATED",
    category: "SESSION",
    award: null,
    createdAt: "2026-11-04T10:00:00.000Z",
    readAt: null,
    pelada: { id: PELADA_ID, name: "Pelada do Fut", image: null },
    daily: { id: SCHEDULED_ID, date: "2026-11-05", time: "21:00" },
    actor: null,
    team: null,
  },
]

export const notificationPreferences: NotificationPreference[] = [
  { category: "ATTENDANCE", emailEnabled: true },
  { category: "SESSION", emailEnabled: false },
  { category: "MEMBERSHIP", emailEnabled: true },
  { category: "AWARDS", emailEnabled: false },
]

const financeList: FinanceOverview["lists"][number] = {
  id: 50,
  name: "Mensalidade de novembro",
  classification: "MONTHLY",
  description: null,
  components: [],
  pixKey: "pix@futspring.com",
  pixRecipient: "Leal",
  qrId: null,
  status: "OPEN",
  dueDate: "2026-11-10",
  dailyId: null,
  dailyLabel: null,
  publishedAt: "2026-11-01T12:00:00.000Z",
  totals: { due: "600.00", received: "250.00", outstanding: "350.00", pendingNotices: 1 },
}

export const financeOverview: FinanceOverview = {
  revision: 3,
  permissions: { manage: true, appointManagers: true, viewCash: true },
  settings: { visibility: "INDIVIDUAL", pixKey: "pix@futspring.com", pixRecipient: "Leal", qrId: null, managerIds: [] },
  balance: "1250.00",
  templates: [],
  lists: [financeList],
  hasMore: false,
}

export const financeDetail: FinanceDetail = {
  collection: financeList,
  charges: members.slice(0, 6).map((m, i) => ({
    id: 600 + i,
    personId: 700 + i,
    userId: m.id,
    name: m.username,
    components: [{ name: "Mensalidade", amount: "100.00" }],
    adjustment: "0.00",
    amountDue: "100.00",
    received: i < 2 ? "100.00" : i === 2 ? "50.00" : "0.00",
    outstanding: i < 2 ? "0.00" : i === 2 ? "50.00" : "100.00",
    paymentStatus: i < 2 ? "PAID" : i === 2 ? "PARTIAL" : "UNPAID",
    overdue: false,
    canceled: false,
    dueDate: "2026-11-10",
    notices: [],
  })),
}
