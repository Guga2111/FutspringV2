import { format } from "date-fns"
import { ptBR } from "date-fns/locale"
import { parseLocalDate } from "@/utils/dates"
import { matchesMembership, type MembershipFilter } from "@/utils/memberFilters"
import type { PeladaAttendanceRow } from "@/types/pelada"

export interface RankedAttendanceRow extends PeladaAttendanceRow {
  // 1-based; players with the same count share a position ("1, 1, 3")
  position: number
}

// Rows arrive sorted by the backend (rate desc); the denominator is the same for everyone,
// so the same sessionsAttended means the same rate
export function rankAttendance(rows: PeladaAttendanceRow[]): RankedAttendanceRow[] {
  return rows.map((row, idx) => ({
    ...row,
    position:
      idx > 0 && rows[idx - 1].sessionsAttended === row.sessionsAttended
        ? rows.findIndex((r) => r.sessionsAttended === row.sessionsAttended) + 1
        : idx + 1,
  }))
}

// "desc": most present first (the backend order); "asc": least present first
export type AttendanceOrder = "desc" | "asc"

export interface AttendanceView {
  membership: MembershipFilter
  order: AttendanceOrder
  query: string
}

// Case- and accent-insensitive: "André" -> "andre"
function normalizeName(name: string): string {
  return name.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLocaleLowerCase("pt-BR").trim()
}

// Filter by type -> rank inside the group -> reverse when ascending -> search.
// Positions always belong to the most present; the order and the search only move or hide rows
export function viewAttendance(rows: PeladaAttendanceRow[], { membership, order, query }: AttendanceView): RankedAttendanceRow[] {
  let ranked = rankAttendance(rows.filter((row) => matchesMembership(row, membership)))
  if (order === "asc") {
    ranked = [...ranked].sort(
      (a, b) =>
        a.sessionsAttended - b.sessionsAttended ||
        a.username.localeCompare(b.username, "pt-BR", { sensitivity: "base" }),
    )
  }
  const q = normalizeName(query)
  return q ? ranked.filter((row) => normalizeName(row.username).includes(q)) : ranked
}

// 0.8333 -> "83%"
export function formatAttendanceRate(rate: number): string {
  return `${Math.round(rate * 100)}%`
}

// "2026-04-26", "2026-10-26" -> "abr 2026 – out 2026"
export function attendancePeriodLabel(from: string, to: string): string {
  const fmt = (iso: string) => format(parseLocalDate(iso), "MMM yyyy", { locale: ptBR })
  return `${fmt(from)} – ${fmt(to)}`
}
