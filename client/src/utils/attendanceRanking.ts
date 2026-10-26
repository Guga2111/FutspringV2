import { format } from "date-fns"
import { ptBR } from "date-fns/locale"
import { parseLocalDate } from "@/utils/dates"
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

// 0.8333 -> "83%"
export function formatAttendanceRate(rate: number): string {
  return `${Math.round(rate * 100)}%`
}

// "2026-04-26", "2026-10-26" -> "abr 2026 – out 2026"
export function attendancePeriodLabel(from: string, to: string): string {
  const fmt = (iso: string) => format(parseLocalDate(iso), "MMM yyyy", { locale: ptBR })
  return `${fmt(from)} – ${fmt(to)}`
}
