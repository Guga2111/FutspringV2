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

// What the caller's confirm button shows. The server re-checks everything; this only decides the UI.
export type SelfConfirmState = "confirmed" | "open" | "notYet" | "full" | "priority"

export interface SelfConfirmInput {
  isConfirmed: boolean
  // ISO instant from when the caller may confirm themselves; null for mensalistas (no window)
  confirmOpensAt: string | null
  confirmedCount: number
  capacity: number
}

// notYet: a diarista before the window opens. full: a diarista in a full session.
// priority: a mensalista in a full session (they take the spot of the diarista confirmed last).
export function getSelfConfirmState(input: SelfConfirmInput, now: Date): SelfConfirmState {
  if (input.isConfirmed) return "confirmed"
  const isMensalista = input.confirmOpensAt === null
  if (!isMensalista && now.getTime() < new Date(input.confirmOpensAt as string).getTime()) return "notYet"
  if (input.confirmedCount >= input.capacity) return isMensalista ? "priority" : "full"
  return "open"
}

// Confirmed players in confirmation order (oldest first); players without confirmedAt last, by name
export function sortByConfirmation(confirmed: PlayerDTO[]): PlayerDTO[] {
  return [...confirmed].sort((a, b) => {
    if (a.confirmedAt && b.confirmedAt) return a.confirmedAt.localeCompare(b.confirmedAt) || a.id - b.id
    if (a.confirmedAt) return -1
    if (b.confirmedAt) return 1
    return byName(a, b)
  })
}
