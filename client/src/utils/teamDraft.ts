import type { DailyDetail, PlayerDTO } from "@/types/daily"

// A team being built in the "Montar times" editor (and the shape PUT /dailies/{id}/teams takes)
export interface DraftTeam {
  name: string
  color: string | null
  playerIds: number[]
}

export const MIN_TEAMS = 2
export const MAX_TEAMS = 10
const NAMES_SHOWN = 5

// The editor starts from the current teams, or numberOfTeams empty "Time 1…N"
export function draftFromDaily(daily: Pick<DailyDetail, "teams" | "numberOfTeams">): DraftTeam[] {
  if (daily.teams.length > 0) {
    return daily.teams.map((team) => ({ name: team.name, color: team.color, playerIds: team.players.map((p) => p.id) }))
  }
  return Array.from({ length: Math.max(MIN_TEAMS, daily.numberOfTeams) }, (_, i) => ({
    name: `Time ${i + 1}`,
    color: null,
    playerIds: [],
  }))
}

// Confirmed players on no team, in confirmation order
export function unassignedPlayers(teams: Pick<DraftTeam, "playerIds">[], confirmed: PlayerDTO[]): PlayerDTO[] {
  const assigned = new Set(teams.flatMap((team) => team.playerIds))
  return confirmed.filter((player) => !assigned.has(player.id))
}

// Moves a player to the end of a team (index) or out of every team (null)
export function moveToTeam(teams: DraftTeam[], playerId: number, target: number | null): DraftTeam[] {
  return teams.map((team, index) => {
    const without = team.playerIds.filter((id) => id !== playerId)
    return index === target ? { ...team, playerIds: [...without, playerId] } : { ...team, playerIds: without }
  })
}

// A new empty team named after the first free "Time N"
export function addTeam(teams: DraftTeam[]): DraftTeam[] {
  if (teams.length >= MAX_TEAMS) return teams
  const names = new Set(teams.map((team) => team.name.trim()))
  let n = teams.length + 1
  while (names.has(`Time ${n}`)) n++
  return [...teams, { name: `Time ${n}`, color: null, playerIds: [] }]
}

// Its players go back to "Sem time"; never below two teams
export function removeTeam(teams: DraftTeam[], index: number): DraftTeam[] {
  if (teams.length <= MIN_TEAMS) return teams
  return teams.filter((_, i) => i !== index)
}

// After the attendance changed (409): whoever is no longer confirmed leaves the draft; new confirmed players
// show up in "Sem time" by themselves, the rest stays as the admin left it
export function reconcileDraft(teams: DraftTeam[], confirmed: PlayerDTO[]): DraftTeam[] {
  const ids = new Set(confirmed.map((player) => player.id))
  return teams.map((team) => ({ ...team, playerIds: team.playerIds.filter((id) => ids.has(id)) }))
}

export interface TeamPendencies {
  // fewer than playersPerTeam players (empty teams are listed apart)
  short: { name: string; count: number }[]
  empty: string[]
  unassigned: PlayerDTO[]
  notEnoughTeams: boolean
  // the session can't start (and the editor can't save): fewer than 2 teams or an empty one
  blocking: boolean
}

export function teamPendencies(
  teams: Pick<DraftTeam, "name" | "playerIds">[],
  confirmed: PlayerDTO[],
  playersPerTeam: number,
): TeamPendencies {
  const empty = teams.filter((team) => team.playerIds.length === 0).map((team) => team.name)
  const short = teams
    .filter((team) => team.playerIds.length > 0 && team.playerIds.length < playersPerTeam)
    .map((team) => ({ name: team.name, count: team.playerIds.length }))
  const notEnoughTeams = teams.length < MIN_TEAMS
  return {
    short,
    empty,
    unassigned: unassignedPlayers(teams, confirmed),
    notEnoughTeams,
    blocking: notEnoughTeams || empty.length > 0,
  }
}

// The daily's own teams as draft-shaped teams (Times section and the start dialog)
export function dailyTeamPendencies(daily: Pick<DailyDetail, "teams" | "confirmedPlayers" | "playersPerTeam">) {
  return teamPendencies(
    daily.teams.map((team) => ({ name: team.name, playerIds: team.players.map((p) => p.id) })),
    daily.confirmedPlayers,
    daily.playersPerTeam,
  )
}

// One pt-BR line per pending item: "Time 2 desfalcado (4 de 5)", "1 confirmado sem time: Bruno", "Time 3 está vazio"
export function pendencyLines(pendencies: TeamPendencies, playersPerTeam: number): string[] {
  const lines: string[] = []
  if (pendencies.notEnoughTeams) lines.push("Monte pelo menos 2 times")
  for (const name of pendencies.empty) lines.push(`${name} está vazio`)
  for (const team of pendencies.short) lines.push(`${team.name} desfalcado (${team.count} de ${playersPerTeam})`)
  const count = pendencies.unassigned.length
  if (count > 0) {
    const names = pendencies.unassigned.slice(0, NAMES_SHOWN).map((p) => p.username).join(", ")
    const more = count > NAMES_SHOWN ? ` e mais ${count - NAMES_SHOWN}` : ""
    lines.push(`${count} ${count === 1 ? "confirmado" : "confirmados"} sem time: ${names}${more}`)
  }
  return lines
}
