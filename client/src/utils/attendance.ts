import type { PlayerDTO } from "@/types/daily"

const byName = (a: PlayerDTO, b: PlayerDTO) => a.username.localeCompare(b.username, "pt-BR", { sensitivity: "base" })

// Confirmed and pending members, each sorted by name. Confirmed players who left the pelada still show as confirmed.
export function splitAttendance(members: PlayerDTO[], confirmed: PlayerDTO[]) {
  const confirmedIds = new Set(confirmed.map((p) => p.id))
  return {
    confirmed: [...confirmed].sort(byName),
    pending: members.filter((m) => !confirmedIds.has(m.id)).sort(byName),
  }
}

// Sorting needs exactly numberOfTeams × playersPerTeam confirmed players (DailyTeamManagementService.sortTeams)
export function canSortTeams(confirmedCount: number, numberOfTeams: number, playersPerTeam: number): boolean {
  return confirmedCount === numberOfTeams * playersPerTeam
}

export function attendanceHint(confirmedCount: number, numberOfTeams: number, playersPerTeam: number): string {
  const required = numberOfTeams * playersPerTeam
  if (confirmedCount === required) return "Lista completa. Os times já podem ser sorteados."
  if (confirmedCount > required) {
    const extra = confirmedCount - required
    return `${extra} ${extra === 1 ? "confirmado a mais" : "confirmados a mais"} para fechar ${numberOfTeams} times de ${playersPerTeam}.`
  }
  return `Faltam ${required - confirmedCount} para fechar ${numberOfTeams} times de ${playersPerTeam}.`
}

export function sortTeamsHint(confirmedCount: number, numberOfTeams: number, playersPerTeam: number): string {
  if (canSortTeams(confirmedCount, numberOfTeams, playersPerTeam)) {
    return "Lista fechada. Sorteie para equilibrar os times pelas estrelas."
  }
  const required = numberOfTeams * playersPerTeam
  return `É preciso ter exatamente ${required} confirmados (${numberOfTeams} × ${playersPerTeam}) para sortear. Hoje são ${confirmedCount}.`
}
