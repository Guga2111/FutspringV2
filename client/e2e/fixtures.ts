// Typed API fixtures for the visual tests. Types come from src/types, so a DTO change breaks `tsc`
// here instead of silently rendering a stale screen. Dates are relative to NOW (the frozen clock).
import type { UserResponseDTO } from "../src/types/auth"
import type { DeletionBlocker, ProfileDTO, PublicUser } from "../src/types/user"
import type { PeladaAwards, PeladaAttendanceDTO, PeladaDetail, PeladaMember, PeladaResponse } from "../src/types/pelada"
import type { DailyDetail, DailyListItem, PlayerDTO, RankingDTO, TeamDTO } from "../src/types/daily"
import type { StatsDTO, UserMatchHistoryDTO, UserStatsTimelineDTO } from "../src/types/stats"
import type { NotificationPreference, NotificationResponse } from "../src/types/notification"
import type { VapidPublicKey } from "../src/types/push"
import type { SubscriptionSummary } from "../src/types/subscription"
import type { PlatformOverview, PlatformPage, PlatformPayment, PlatformPayments, PlatformPeladaDetail, PlatformPeladaItem, PlatformUserItem } from "../src/types/platform"
import type { Invitation, InvitationStatus, InviteLink } from "../src/types/invitation"
import type { FinanceAudit, FinanceDetail, FinanceMovement, FinanceOverview, FinancePage, FinanceSettings, FinanceTemplate } from "../src/types/finance"

// Wednesday 2026-11-04 15:00 in São Paulo
export const NOW = new Date("2026-11-04T18:00:00.000Z")

export const PELADA_ID = 1
export const SCHEDULED_ID = 203
export const LIVE_ID = 202
export const FINISHED_ID = 201
export const CONFIRMED_ID = 204

const NAMES = ["Leal", "Bruno", "Caio", "Diego", "Edu", "Fabio", "Gabriel", "Hugo", "Igor", "João", "Kaique", "Lucas"]
const POSITIONS = ["GOLEIRO", "ZAGUEIRO", "MEIO", "ATACANTE"]

export const me: UserResponseDTO = {
  id: 1,
  email: "leal@futspring.com",
  username: "Leal",
  image: null,
  backgroundImage: null,
  position: "MEIO",
  // the platform admin's panel (/admin)
  isPlatformAdmin: true,
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
  isDeleted: false,
})
const publicUser = (m: PeladaMember): PublicUser => ({ id: m.id, username: m.username, image: null, position: m.position, isDeleted: false })

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

// a trial far from its end: no notice, every button enabled (the read-only screens override it)
export const trialSubscription: SubscriptionSummary = {
  status: "TRIAL",
  validUntil: "2026-12-01T18:00:00.000Z",
  writable: true,
}
export const readOnlySubscription: SubscriptionSummary = { status: "READ_ONLY", validUntil: "2026-11-01T18:00:00.000Z", writable: false }
// paid until 02/11: locks on 09/11
export const pastDueSubscription: SubscriptionSummary = { status: "PAST_DUE", validUntil: "2026-11-02T18:00:00.000Z", writable: true }

export const myPeladas: PeladaResponse[] = [
  {
    id: PELADA_ID,
    name: "Pelada do Fut",
    dayOfWeek: "THURSDAY",
    timeOfDay: "21:00",
    duration: 2,
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
    subscription: trialSubscription,
  },
  {
    id: 2,
    name: "Fut de Domingo",
    dayOfWeek: "SUNDAY",
    timeOfDay: "09:00",
    duration: 1,
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
    subscription: trialSubscription,
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
    wonSession: null,
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
      // the caller won two, lost one and sat one out, so every gallery filter has sessions
      wonSession: [true, false, null, true][i],
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
  creatorId: 1,
  subscription: trialSubscription,
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

// Confirmed session after a withdrawal: Branco is one short (4 of 5) and João confirmed without a team
export const confirmedDaily: DailyDetail = {
  ...baseDaily,
  id: CONFIRMED_ID,
  dailyDate: "2026-11-05",
  dailyTime: "21:00",
  status: "CONFIRMED",
  isFinished: false,
  confirmedPlayers: confirmed(10),
  teams: teams.map((team, i) => {
    const players = i === 1 ? team.players.slice(0, 4) : team.players
    const total = players.reduce((sum, p) => sum + p.stars, 0)
    return { ...team, players, totalStars: total, averageStars: total / players.length }
  }),
  matches: [],
  playerStats: [],
  leagueTable: [],
  award: null,
  startsAt: "2026-11-06T00:00:00.000Z",
  bumps: [],
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
  // sessions won (titles); matchWins are the matches won
  wins: 9,
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
      result: i % 2 === 0 ? "W" : "L",
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

// the Convites page of the pelada: invited accounts that aren't members, sent by the admins
const guest = (id: number, username: string): PublicUser => ({ id, username, image: null, position: "MEIO", isDeleted: false })
const peladaInvitation = (
  id: number,
  recipient: PublicUser,
  status: InvitationStatus,
  createdAt: string,
  extra: Partial<Invitation> = {},
): Invitation => ({
  id,
  peladaId: PELADA_ID,
  pelada: { id: PELADA_ID, name: "Pelada do Fut", image: null, dayOfWeek: "THURSDAY", timeOfDay: "21:00" },
  recipient,
  sender: publicUser(members[0]),
  status,
  createdAt,
  expiresAt: new Date(Date.parse(createdAt) + 7 * 86_400_000).toISOString(),
  respondedAt: null,
  canceledAt: null,
  isMember: false,
  ...extra,
})

export const peladaInvitationsPending: Invitation[] = [
  peladaInvitation(910, guest(101, "Marquinhos"), "PENDING", "2026-11-03T22:00:00.000Z"),
  peladaInvitation(911, guest(102, "Zé Roberto"), "PENDING", "2026-10-30T15:00:00.000Z", { sender: publicUser(members[4]) }),
]

export const peladaInvitationsHistory: Invitation[] = [
  peladaInvitation(905, guest(103, "Tiago Lima"), "ACCEPTED", "2026-10-28T19:00:00.000Z", { respondedAt: "2026-10-29T12:00:00.000Z", isMember: true }),
  peladaInvitation(904, guest(104, "Rafa Souza"), "DECLINED", "2026-10-26T19:00:00.000Z", { respondedAt: "2026-10-27T09:30:00.000Z" }),
  peladaInvitation(903, guest(105, "Bruno Costa"), "CANCELED", "2026-10-22T19:00:00.000Z", { canceledAt: "2026-10-23T10:00:00.000Z" }),
  peladaInvitation(902, guest(106, "Caio Mendes"), "EXPIRED", "2026-10-15T19:00:00.000Z"),
]

export const inviteLink: InviteLink = {
  token: "Mw.8f3a9c2e7b1d4f60a5c8e2b7d9f1a3c5",
  createdAt: "2026-11-02T20:00:00.000Z",
  expiresAt: "2026-11-09T20:00:00.000Z",
  creator: publicUser(members[0]),
}

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
  { category: "ATTENDANCE", emailEnabled: true, pushEnabled: true },
  { category: "SESSION", emailEnabled: false, pushEnabled: true },
  { category: "MEMBERSHIP", emailEnabled: true, pushEnabled: true },
  { category: "AWARDS", emailEnabled: false, pushEnabled: false },
]

// a P-256 public key's shape (65 bytes, base64url); the browser is mocked, so it is never used to encrypt
export const vapidPublicKey: VapidPublicKey = {
  publicKey: "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U",
}

export const QR_ID = 77

const financeList: FinanceOverview["lists"][number] = {
  id: 50,
  name: "Mensalidade de novembro",
  classification: "MONTHLY",
  description: null,
  components: [],
  pixKey: "pix@futspring.com",
  pixRecipient: "Leal",
  qrId: QR_ID,
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
  settings: { visibility: "INDIVIDUAL", pixKey: "pix@futspring.com", pixRecipient: "Leal", qrId: QR_ID, managerIds: [1, 5] },
  balance: "1250.00",
  templates: ([["Mensalidade", "MONTHLY", "100.00"], ["Diária", "DAILY", "20.00"], ["Evento", "OTHER", "0.00"]] as const).map(
    ([name, classification, amount], i): FinanceTemplate => ({
      id: i === 0 ? 10 : null,
      name,
      classification,
      description: null,
      components: [{ name: "Campo", amount }],
      pixKey: "pix@futspring.com",
      pixRecipient: "Leal",
      qrId: QR_ID,
      archived: false,
    }),
  ),
  lists: [financeList],
  hasMore: false,
  total: 1,
}

// what a member receives in individual mode: only the lists with their charge, totals of their own charge
export const financeMemberOverview: FinanceOverview = {
  ...financeOverview,
  permissions: { manage: false, appointManagers: false, viewCash: false },
  settings: { ...financeOverview.settings, managerIds: [] },
  balance: null,
  templates: [],
  lists: [{ ...financeList, totals: { due: "100.00", received: "50.00", outstanding: "50.00", pendingNotices: 0 } }],
}

export const financeMovements: FinancePage<FinanceMovement> = {
  items: [
    ["RECEIPT", "100.00", "Mensalidade de novembro · Leal"],
    ["EXPENSE", "-350.00", "Aluguel do campo"],
    ["RECEIPT", "50.00", "Mensalidade de novembro · Caio"],
  ].map(([kind, amount, description], i): FinanceMovement => ({
    id: 900 - i,
    listId: 50,
    dailyId: null,
    dailyLabel: null,
    chargeId: null,
    noticeId: null,
    originalId: null,
    kind: kind as FinanceMovement["kind"],
    amount,
    obligationDelta: "0.00",
    effectiveDate: "2026-11-02",
    method: "PIX",
    description,
    category: kind === "EXPENSE" ? "Campo" : null,
    createdBy: 1,
    createdAt: "2026-11-02T12:00:00.000Z",
    reversed: false,
  })),
  hasMore: true,
  total: 23,
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

export const financeMemberDetail: FinanceDetail = {
  collection: financeMemberOverview.lists[0],
  charges: [{ ...financeDetail.charges[2], userId: me.id, name: me.username }],
}

// the Histórico tab: a payment, an adjusted value with its reason, the publication, a settings change and an expense,
// with before/after in the JSON shape the backend stores (the views it returned)
const auditBefore: FinanceDetail = {
  ...financeDetail,
  charges: financeDetail.charges.map((c, i) => (i === 2 ? { ...c, received: "0.00", outstanding: "100.00", paymentStatus: "UNPAID" } : c)),
}
const adjusted: FinanceDetail = {
  ...financeDetail,
  charges: financeDetail.charges.map((c, i) => (i === 3 ? { ...c, adjustment: "-20.00", amountDue: "80.00", outstanding: "80.00" } : c)),
}
const draft: FinanceDetail = { collection: { ...financeList, status: "DRAFT", publishedAt: null }, charges: auditBefore.charges }
const settingsBefore: FinanceSettings = { ...financeOverview.settings, visibility: "TRANSPARENT", pixRecipient: null }
const actor = members[0]
const audit = (id: number, operation: FinanceAudit["operation"], target: string, createdAt: string, before: unknown, after: unknown, reason: string | null = null): FinanceAudit => ({
  id, actorId: actor.id, actorName: actor.username, operation, target, reason, createdAt,
  beforeData: before === null ? null : JSON.stringify(before), afterData: after === null ? null : JSON.stringify(after),
})
export const financeAudit: FinancePage<FinanceAudit> = {
  items: [
    audit(805, "RECEIVE", `CHARGE:50:${financeDetail.charges[2].id}`, "2026-11-03T22:30:00.000Z", auditBefore, financeDetail),
    audit(804, "CHARGE_SAVE", `CHARGE:50:${financeDetail.charges[3].id}`, "2026-11-02T14:10:00.000Z", financeDetail, adjusted, "Chegou só no segundo tempo"),
    audit(803, "CASH", "CASH", "2026-11-02T12:00:00.000Z", null, financeMovements.items[1]),
    audit(802, "PUBLISH", "LIST:50", "2026-11-01T12:00:00.000Z", draft, auditBefore),
    audit(801, "SETTINGS", "SETTINGS", "2026-10-30T20:00:00.000Z", settingsBefore, financeOverview.settings),
  ],
  hasMore: false,
  total: 5,
}

// /settings: nothing blocks deleting the account; the "pending items" test answers these instead
export const deletionBlockers: DeletionBlocker[] = []
export const pendingDeletionBlockers: DeletionBlocker[] = [
  { type: "CREATOR", peladaId: PELADA_ID, peladaName: "Pelada do Fut" },
  { type: "SOLE_FINANCE_MANAGER", peladaId: 2, peladaName: "Racha de Quarta" },
]

// ---- the platform admin's panel (/admin): validity is the end of a day in São Paulo (23:59:59 -03:00)

const endOfDay = (date: string) => new Date(`${date}T23:59:59-03:00`).toISOString()
const at = (date: string, time = "10:00") => new Date(`${date}T${time}:00-03:00`).toISOString()
const leal = publicUser(members[0])
const bruno = publicUser(members[1])
const caio = publicUser(members[2])

export const platformOverview: PlatformOverview = {
  counts: { TRIAL: 6, ACTIVE: 4, PAST_DUE: 1, READ_ONLY: 2, PENDING: 1, EXEMPT: 1 },
  trialsEndingSoon: [
    { id: 12, name: "Racha de Quinta", date: endOfDay("2026-11-07") },
    { id: 14, name: "Futebol dos Amigos", date: endOfDay("2026-11-10") },
  ],
  pixClaimed: [{ id: 11, name: "Pelada do Parque", date: at("2026-11-03", "20:15") }],
  revenueThisMonth: "218.90",
  revenueByMonth: [
    "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "19.90", "39.80", "218.90", "218.90",
  ].map((total, i) => ({ month: `${i < 2 ? 2025 : 2026}-${String(((i + 10) % 12) + 1).padStart(2, "0")}`, total })),
  recurringEstimate: "76.48",
  users: { total: 148, last30Days: 23 },
}

const peladaItem = (id: number, name: string, creator: PublicUser, status: PlatformPeladaItem["status"], validUntil: string | null, extra: Partial<PlatformPeladaItem> = {}): PlatformPeladaItem => ({
  id, name, creator, memberCount: 18, status, validUntil, founder: false, pixClaimedAt: null, lastSessionDate: "2026-11-01", ...extra,
})

export const platformPeladas: PlatformPage<PlatformPeladaItem> = {
  items: [
    peladaItem(PELADA_ID, "Pelada do Fut", leal, "EXEMPT", null, { memberCount: 24 }),
    peladaItem(11, "Pelada do Parque", bruno, "TRIAL", endOfDay("2026-11-20"), { pixClaimedAt: at("2026-11-03", "20:15"), memberCount: 29 }),
    peladaItem(12, "Racha de Quinta", caio, "TRIAL", endOfDay("2026-11-07"), { lastSessionDate: "2026-10-29" }),
    peladaItem(13, "Society da Firma", bruno, "ACTIVE", endOfDay("2027-10-15"), { founder: true, memberCount: 42 }),
    peladaItem(15, "Bola de Domingo", caio, "PAST_DUE", endOfDay("2026-11-01"), { memberCount: 12 }),
    peladaItem(16, "Futsal da Vila", leal, "READ_ONLY", endOfDay("2026-10-10"), { memberCount: 9, lastSessionDate: null }),
  ],
  total: 15,
}

const payment = (id: number, amount: string, paidOn: string, plan: PlatformPayment["plan"], periodEnd: string, extra: Partial<PlatformPayment> = {}): PlatformPayment => ({
  id, peladaId: 13, peladaName: "Society da Firma", payerId: bruno.id, payerName: bruno.username, amount, paidOn, plan,
  periodStart: at(paidOn), periodEnd: endOfDay(periodEnd), recordedBy: leal, note: null, voidedAt: null, voidReason: null,
  createdAt: at(paidOn, "12:00"), ...extra,
})

export const platformPelada: PlatformPeladaDetail = {
  pelada: { id: 13, name: "Society da Firma", image: null, creator: bruno, creatorEmail: "bruno@futspring.com", memberCount: 42, lastSessionDate: "2026-11-01" },
  subscription: {
    kind: "PAID",
    status: "ACTIVE",
    writable: true,
    validUntil: endOfDay("2027-10-15"),
    plan: "ANNUAL",
    founder: true,
    pixClaimedAt: at("2026-11-03", "20:15"),
    claimedPlan: "ANNUAL",
    prices: [{ plan: "MONTHLY", amount: "29.90" }, { plan: "ANNUAL", amount: "299.00" }],
    createdAt: at("2026-09-15"),
    updatedAt: at("2026-10-15"),
  },
  payments: [
    payment(3, "199.00", "2026-10-15", "ANNUAL", "2027-10-15", { note: "Preço de fundadora" }),
    payment(2, "19.90", "2026-10-14", "MONTHLY", "2026-11-14", { voidedAt: at("2026-10-15", "09:00"), voidReason: "Registrado no plano errado" }),
  ],
  events: [
    { id: 5, type: "PIX_CLAIMED", actor: bruno, kindBefore: "PAID", kindAfter: "PAID", validUntilBefore: endOfDay("2027-10-15"), validUntilAfter: endOfDay("2027-10-15"), note: null, createdAt: at("2026-11-03", "20:15") },
    { id: 4, type: "PAYMENT_RECORDED", actor: leal, kindBefore: "TRIAL", kindAfter: "PAID", validUntilBefore: endOfDay("2026-10-15"), validUntilAfter: endOfDay("2027-10-15"), note: "Preço de fundadora", createdAt: at("2026-10-15", "12:00") },
    { id: 3, type: "PAYMENT_VOIDED", actor: leal, kindBefore: "PAID", kindAfter: "PAID", validUntilBefore: endOfDay("2026-11-14"), validUntilAfter: endOfDay("2026-11-14"), note: "Registrado no plano errado", createdAt: at("2026-10-15", "09:00") },
    { id: 2, type: "FOUNDER_CHANGED", actor: leal, kindBefore: "TRIAL", kindAfter: "TRIAL", validUntilBefore: endOfDay("2026-10-15"), validUntilAfter: endOfDay("2026-10-15"), note: null, createdAt: at("2026-09-20") },
    { id: 1, type: "CREATED", actor: null, kindBefore: null, kindAfter: "TRIAL", validUntilBefore: null, validUntilAfter: endOfDay("2026-10-15"), note: null, createdAt: at("2026-09-15") },
  ],
}

export const platformPayments: PlatformPayments = {
  items: [
    payment(4, "19.90", "2026-11-02", "MONTHLY", "2026-12-02", { peladaId: 11, peladaName: "Pelada do Parque" }),
    ...platformPelada.payments,
    payment(1, "19.90", "2026-09-01", "MONTHLY", "2026-10-01", { peladaId: null, peladaName: "Pelada Antiga", payerName: "Caio" }),
  ],
  total: 4,
  sum: "238.80",
}

export const platformUsers: PlatformPage<PlatformUserItem> = {
  items: [
    { id: 30, username: "Novato", email: "novato@exemplo.com", createdAt: at("2026-11-02"), peladasCreated: [], memberOf: 1, trialUsedAt: null, isDeleted: false },
    { id: bruno.id, username: bruno.username, email: "bruno@futspring.com", createdAt: at("2026-09-10"), peladasCreated: [{ id: 11, name: "Pelada do Parque" }, { id: 13, name: "Society da Firma" }], memberOf: 3, trialUsedAt: at("2026-09-15"), isDeleted: false },
    { id: 31, username: "Ex-jogador", email: "deleted-31@invalid", createdAt: null, peladasCreated: [], memberOf: 0, trialUsedAt: null, isDeleted: true },
  ],
  total: 3,
}
