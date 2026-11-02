import { describe, it, expect } from "vitest"
import { filterGallery, formatRangeLabel, formatSessionCount, galleryCounts, gallerySessions } from "./sessionGallery"
import type { DailyListItem, DailyStatus } from "@/types/daily"

const daily = (id: number, dailyDate: string, wonSession: boolean | null, status: DailyStatus = "FINISHED"): DailyListItem => ({
  id,
  dailyDate,
  dailyTime: "08:00",
  status,
  confirmedPlayerCount: 0,
  isFinished: status === "FINISHED",
  teamCount: 0,
  matchCount: 0,
  championImage: null,
  isConfirmed: false,
  capacity: 10,
  startsAt: `${dailyDate}T11:00:00Z`,
  confirmOpensAt: null,
  wonSession,
})

const sessions = [
  daily(1, "2026-09-27", true),
  daily(2, "2026-10-04", false),
  daily(3, "2026-10-11", null),
  daily(4, "2026-10-18", true),
  daily(5, "2026-11-05", null, "SCHEDULED"),
  daily(6, "2026-10-25", null, "CANCELED"),
]

describe("gallerySessions", () => {
  it("keeps finished sessions only, newest first", () => {
    expect(gallerySessions(sessions).map((d) => d.id)).toEqual([4, 3, 2, 1])
  })
})

describe("filterGallery", () => {
  const finished = gallerySessions(sessions)

  it("filters by the caller's result; a session they didn't play is only in Todas", () => {
    expect(filterGallery(finished, { result: "ALL", range: undefined }).map((d) => d.id)).toEqual([4, 3, 2, 1])
    expect(filterGallery(finished, { result: "WON", range: undefined }).map((d) => d.id)).toEqual([4, 1])
    expect(filterGallery(finished, { result: "NOT_WON", range: undefined }).map((d) => d.id)).toEqual([2])
  })

  it("keeps both ends of the range", () => {
    const range = { from: new Date(2026, 9, 4), to: new Date(2026, 9, 18) }
    expect(filterGallery(finished, { result: "ALL", range }).map((d) => d.id)).toEqual([4, 3, 2])
  })

  it("treats a range with only a start as that single day", () => {
    expect(filterGallery(finished, { result: "ALL", range: { from: new Date(2026, 9, 11) } }).map((d) => d.id)).toEqual([3])
  })
})

describe("galleryCounts", () => {
  it("counts every filter inside the range", () => {
    const finished = gallerySessions(sessions)
    expect(galleryCounts(finished, undefined)).toEqual({ ALL: 4, WON: 2, NOT_WON: 1 })
    expect(galleryCounts(finished, { from: new Date(2026, 9, 1), to: new Date(2026, 9, 31) })).toEqual({ ALL: 3, WON: 1, NOT_WON: 1 })
  })
})

describe("formatRangeLabel", () => {
  it("names the period", () => {
    expect(formatRangeLabel(undefined)).toBe("Todo o período")
    expect(formatRangeLabel({ from: new Date(2026, 9, 8) })).toBe("08/10/2026")
    expect(formatRangeLabel({ from: new Date(2026, 9, 1), to: new Date(2026, 9, 31) })).toBe("01/10 – 31/10/2026")
    expect(formatRangeLabel({ from: new Date(2025, 11, 15), to: new Date(2026, 0, 10) })).toBe("15/12/2025 – 10/01/2026")
  })
})

describe("formatSessionCount", () => {
  it("pluralizes", () => {
    expect(formatSessionCount(1)).toBe("1 sessão")
    expect(formatSessionCount(12)).toBe("12 sessões")
  })
})
