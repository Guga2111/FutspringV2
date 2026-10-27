import type { PeladaMember } from "@/types/pelada"

// The four positions offered in the app; profiles may still hold the English values
export type PositionFilter = "ALL" | "GOLEIRO" | "ZAGUEIRO" | "MEIO" | "ATACANTE"

const NORMALIZED: Record<string, Exclude<PositionFilter, "ALL">> = {
  GOLEIRO: "GOLEIRO",
  GOALKEEPER: "GOLEIRO",
  ZAGUEIRO: "ZAGUEIRO",
  DEFENDER: "ZAGUEIRO",
  MEIO: "MEIO",
  MIDFIELDER: "MEIO",
  ATACANTE: "ATACANTE",
  FORWARD: "ATACANTE",
}

export const POSITION_FILTERS: { value: PositionFilter; label: string }[] = [
  { value: "ALL", label: "Todos" },
  { value: "GOLEIRO", label: "Goleiro" },
  { value: "ZAGUEIRO", label: "Zagueiro" },
  { value: "MEIO", label: "Meio" },
  { value: "ATACANTE", label: "Atacante" },
]

export function normalizePosition(position: string | null): Exclude<PositionFilter, "ALL"> | null {
  return position ? (NORMALIZED[position] ?? null) : null
}

export function countByPosition(members: PeladaMember[]): Record<PositionFilter, number> {
  const counts: Record<PositionFilter, number> = { ALL: members.length, GOLEIRO: 0, ZAGUEIRO: 0, MEIO: 0, ATACANTE: 0 }
  for (const member of members) {
    const position = normalizePosition(member.position)
    if (position) counts[position] += 1
  }
  return counts
}

export type MembershipFilter = "ALL" | "MENSALISTA" | "DIARISTA"

export const MEMBERSHIP_FILTERS: { value: Exclude<MembershipFilter, "ALL">; label: string }[] = [
  { value: "MENSALISTA", label: "Mensalistas" },
  { value: "DIARISTA", label: "Diaristas" },
]

// Members, attendance rows or anything else that carries the member's type
type HasMembership = { isMensalista: boolean }

export function countByMembership(members: HasMembership[]): Record<MembershipFilter, number> {
  const mensalistas = members.filter((m) => m.isMensalista).length
  return { ALL: members.length, MENSALISTA: mensalistas, DIARISTA: members.length - mensalistas }
}

export function matchesMembership(member: HasMembership, membership: MembershipFilter): boolean {
  return membership === "ALL" || member.isMensalista === (membership === "MENSALISTA")
}

// Sorted by name: the API returns members in no particular order
export function filterMembers(
  members: PeladaMember[],
  query: string,
  position: PositionFilter,
  membership: MembershipFilter = "ALL",
): PeladaMember[] {
  const q = query.trim().toLocaleLowerCase("pt-BR")
  return members
    .filter(
      (m) =>
        (position === "ALL" || normalizePosition(m.position) === position) &&
        matchesMembership(m, membership) &&
        m.username.toLocaleLowerCase("pt-BR").includes(q),
    )
    .sort((a, b) => a.username.localeCompare(b.username, "pt-BR", { sensitivity: "base" }))
}

// Short pt-BR label of any position value ("MIDFIELDER" -> "Meio"), or null
export function positionShortLabel(position: string | null): string | null {
  const normalized = normalizePosition(position)
  return POSITION_FILTERS.find((f) => f.value === normalized)?.label ?? null
}
