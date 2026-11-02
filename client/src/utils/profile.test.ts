import { describe, expect, it } from "vitest"
import type { MatchHistoryRow, StatsDTO, TimelinePoint } from "@/types/stats"
import {
  filterTimeline,
  formatAverage,
  latestDate,
  profileRates,
  recentForm,
  timelineTotals,
  toSessionResult,
} from "@/utils/profile"

const stats = (patch: Partial<StatsDTO> = {}): StatsDTO => ({
  userId: 1,
  username: "Leal",
  goals: 42,
  assists: 27,
  matchesPlayed: 96,
  wins: 6,
  sessionsPlayed: 24,
  matchWins: 51,
  wiltballWins: 0,
  artilheiroWins: 0,
  garcomWins: 0,
  puskasDates: [],
  ...patch,
})

const row = (dailyId: number, date: string, result = "W"): MatchHistoryRow => ({
  dailyId,
  date,
  peladaId: 1,
  peladaName: "Pelada",
  goals: 1,
  assists: 0,
  matchesPlayed: 4,
  wins: 2,
  result,
})

const point = (date: string, goals = 1): TimelinePoint => ({ date, goals, assists: 2, wins: 0, matchesPlayed: 3 })

describe("profileRates", () => {
  it("computes rates and per-session averages", () => {
    const rates = profileRates(stats())
    expect(rates.winPct).toBe(53)
    expect(rates.titlePct).toBe(25)
    expect(rates.goalsPerSession).toBeCloseTo(1.75)
    expect(rates.contributionsPerSession).toBeCloseTo(69 / 24)
  })

  it("is zero without sessions or matches", () => {
    const rates = profileRates(stats({ goals: 0, assists: 0, matchesPlayed: 0, matchWins: 0, wins: 0, sessionsPlayed: 0 }))
    expect(rates).toEqual({ winPct: 0, titlePct: 0, goalsPerSession: 0, assistsPerSession: 0, contributionsPerSession: 0 })
  })
})

describe("formatAverage", () => {
  it("uses a pt-BR decimal comma", () => {
    expect(formatAverage(1.75)).toBe("1,8")
    expect(formatAverage(0)).toBe("0,0")
  })
})

describe("recentForm", () => {
  it("returns the newest sessions first, at most n", () => {
    const rows = [row(1, "2026-09-01"), row(3, "2026-10-01"), row(2, "2026-09-15"), row(4, "2026-10-08"), row(5, "2026-10-15"), row(6, "2026-10-22")]
    expect(recentForm(rows).map((r) => r.dailyId)).toEqual([6, 5, 4, 3, 2])
  })

  it("returns fewer when there are fewer sessions", () => {
    expect(recentForm([row(1, "2026-09-01")])).toHaveLength(1)
    expect(recentForm([])).toEqual([])
  })
})

describe("toSessionResult", () => {
  it("maps the API values", () => {
    expect(toSessionResult("W")).toBe("W")
    expect(toSessionResult("l")).toBe("L")
    expect(toSessionResult("—")).toBe("—")
    expect(toSessionResult("")).toBe("—")
  })
})

describe("filterTimeline", () => {
  const now = new Date(2026, 10, 4, 15, 0) // 2026-11-04 15:00 local
  const points = [point("2026-11-04"), point("2026-10-28"), point("2026-10-27"), point("2026-10-05"), point("2026-10-04"), point("2026-08-06"), point("2026-08-05")]

  it("keeps the range's edge day and sorts oldest first", () => {
    expect(filterTimeline(points, "7d", now).map((p) => p.date)).toEqual(["2026-10-28", "2026-11-04"])
    expect(filterTimeline(points, "30d", now).map((p) => p.date)).toEqual(["2026-10-05", "2026-10-27", "2026-10-28", "2026-11-04"])
    expect(filterTimeline(points, "90d", now)[0].date).toBe("2026-08-06")
  })
})

describe("timelineTotals", () => {
  it("sums each metric", () => {
    expect(timelineTotals([point("2026-10-01", 2), point("2026-10-08", 3)])).toEqual({ goals: 5, assists: 4, matchesPlayed: 6 })
    expect(timelineTotals([])).toEqual({ goals: 0, assists: 0, matchesPlayed: 0 })
  })
})

describe("latestDate", () => {
  it("returns the latest ISO date or null", () => {
    expect(latestDate(["2026-09-10", "2026-10-15", "2026-01-01"])).toBe("2026-10-15")
    expect(latestDate([])).toBeNull()
    expect(latestDate(undefined)).toBeNull()
  })
})
