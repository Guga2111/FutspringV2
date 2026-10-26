import { describe, expect, it } from "vitest"
import { attendancePeriodLabel, formatAttendanceRate, rankAttendance } from "./attendanceRanking"
import type { PeladaAttendanceRow } from "@/types/pelada"

const row = (userId: number, sessionsAttended: number, rate: number): PeladaAttendanceRow => ({
  userId,
  username: `p${userId}`,
  userImage: null,
  sessionsAttended,
  rate,
})

describe("rankAttendance", () => {
  it("shares the position between ties and skips the next ones", () => {
    const ranked = rankAttendance([row(1, 6, 1), row(2, 6, 1), row(3, 4, 0.66), row(4, 0, 0), row(5, 0, 0)])

    expect(ranked.map((r) => r.position)).toEqual([1, 1, 3, 4, 4])
  })

  it("returns an empty list for no rows", () => {
    expect(rankAttendance([])).toEqual([])
  })
})

describe("formatAttendanceRate", () => {
  it("rounds to a whole percentage", () => {
    expect(formatAttendanceRate(5 / 6)).toBe("83%")
    expect(formatAttendanceRate(1)).toBe("100%")
    expect(formatAttendanceRate(0)).toBe("0%")
  })
})

describe("attendancePeriodLabel", () => {
  it("formats both ends as month and year in pt-BR", () => {
    expect(attendancePeriodLabel("2026-04-26", "2026-10-26")).toBe("abr 2026 – out 2026")
  })
})
