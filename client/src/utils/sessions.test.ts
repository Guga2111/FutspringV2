import { describe, it, expect } from "vitest"
import { formatLongDate, formatRelativeSessionDay, formatSessionMeta, groupDailiesByMonth, pickNextSession } from "./sessions"
import type { DailyListItem, DailyStatus } from "@/types/daily"

const daily = (id: number, dailyDate: string, status: DailyStatus = "FINISHED"): DailyListItem => ({
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
})

const now = new Date(2026, 9, 4, 10, 0)

describe("pickNextSession", () => {
  it("returns the earliest open session from today on", () => {
    const list = [
      daily(1, "2026-10-11", "SCHEDULED"),
      daily(2, "2026-10-04", "CONFIRMED"),
      daily(3, "2026-10-01", "SCHEDULED"),
      daily(4, "2026-10-05", "CANCELED"),
    ]
    expect(pickNextSession(list, now)?.id).toBe(2)
  })

  it("returns null without open sessions", () => {
    expect(pickNextSession([daily(1, "2026-10-11", "FINISHED")], now)).toBeNull()
  })
})

describe("groupDailiesByMonth", () => {
  it("groups by month, newest first", () => {
    const months = groupDailiesByMonth([daily(1, "2026-08-30"), daily(2, "2026-09-27"), daily(3, "2026-09-06")])
    expect(months.map((m) => m.label)).toEqual(["Setembro 2026", "Agosto 2026"])
    expect(months[0].dailies.map((d) => d.id)).toEqual([2, 3])
  })
})

describe("formatLongDate", () => {
  it("formats weekday, day and month in pt-BR", () => {
    expect(formatLongDate("2026-09-27")).toBe("Domingo, 27 de setembro")
  })
})

describe("formatSessionMeta", () => {
  it("leaves out zero counts and pluralizes", () => {
    expect(formatSessionMeta({ dailyTime: "08:00", teamCount: 4, matchCount: 6 })).toBe("08:00 · 4 times · 6 partidas")
    expect(formatSessionMeta({ dailyTime: "21:00", teamCount: 0, matchCount: 1 })).toBe("21:00 · 1 partida")
  })
})

describe("formatRelativeSessionDay", () => {
  it("says today and tomorrow, else the full date", () => {
    expect(formatRelativeSessionDay("2026-10-04", now)).toBe("Hoje, domingo")
    expect(formatRelativeSessionDay("2026-10-05", now)).toBe("Amanhã, segunda-feira")
    expect(formatRelativeSessionDay("2026-10-08", now)).toBe("Quinta-feira, 8 de outubro")
  })
})
