import { describe, expect, it } from "vitest"
import { attendancePeriodLabel, formatAttendanceRate, rankAttendance, viewAttendance } from "./attendanceRanking"
import type { PeladaAttendanceRow } from "@/types/pelada"

const row = (
  userId: number,
  sessionsAttended: number,
  rate: number,
  { username = `p${userId}`, isMensalista = false }: { username?: string; isMensalista?: boolean } = {},
): PeladaAttendanceRow => ({
  userId,
  username,
  userImage: null,
  sessionsAttended,
  rate,
  isMensalista,
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

describe("viewAttendance", () => {
  // Backend order: rate desc, attended desc, username
  const rows = [
    row(1, 10, 1, { username: "Diego", isMensalista: true }),
    row(2, 8, 0.8, { username: "André" }),
    row(3, 8, 0.8, { username: "Bruno", isMensalista: true }),
    row(4, 5, 0.5, { username: "Carla" }),
    row(5, 5, 0.5, { username: "Ana", isMensalista: true }),
    row(6, 0, 0, { username: "Zeca" }),
  ]
  const all = { membership: "ALL", order: "desc", query: "" } as const

  it("is the plain ranking with the default view", () => {
    expect(viewAttendance(rows, all)).toEqual(rankAttendance(rows))
  })

  it("recomputes positions inside the filtered group", () => {
    const view = viewAttendance(rows, { ...all, membership: "DIARISTA" })

    expect(view.map((r) => [r.userId, r.position])).toEqual([
      [2, 1],
      [4, 2],
      [6, 3],
    ])
    expect(viewAttendance(rows, { ...all, membership: "MENSALISTA" }).map((r) => r.position)).toEqual([1, 2, 3])
  })

  it("reverses the order with ties by name and keeps the positions", () => {
    const view = viewAttendance(rows, { ...all, order: "asc" })

    expect(view.map((r) => r.username)).toEqual(["Zeca", "Ana", "Carla", "André", "Bruno", "Diego"])
    expect(view.map((r) => r.position)).toEqual([6, 4, 4, 2, 2, 1])
  })

  it("searches ignoring case and accents without changing positions", () => {
    const view = viewAttendance(rows, { ...all, query: " ANDRE " })

    expect(view.map((r) => [r.userId, r.position])).toEqual([[2, 2]])
    expect(viewAttendance(rows, { ...all, query: "an" }).map((r) => r.userId)).toEqual([2, 5])
  })

  it("returns no rows when the search matches nobody", () => {
    expect(viewAttendance(rows, { ...all, query: "xyz" })).toEqual([])
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
