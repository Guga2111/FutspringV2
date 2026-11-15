import { describe, it, expect } from "vitest"
import { formatLongDate, formatRelativeSessionDay, formatSessionMeta, groupDailiesByMonth, pickNextSession, pickRecentSessions } from "@/utils/sessions"
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
  capacity: 10,
  startsAt: `${dailyDate}T11:00:00Z`,
  confirmOpensAt: null,
  wonSession: null,
})

const zone = "America/Sao_Paulo"
const now = new Date("2026-10-04T13:00:00Z")

describe("pickNextSession", () => {
  it("returns the earliest open session from today on", () => {
    const list = [
      daily(1, "2026-10-11", "SCHEDULED"),
      daily(2, "2026-10-04", "CONFIRMED"),
      daily(3, "2026-10-01", "SCHEDULED"),
      daily(4, "2026-10-05", "CANCELED"),
    ]
    expect(pickNextSession(list, zone, now)?.id).toBe(2)
  })

  it("returns null without open sessions", () => {
    expect(pickNextSession([daily(1, "2026-10-11", "FINISHED")], zone, now)).toBeNull()
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
    expect(formatRelativeSessionDay("2026-10-04", zone, now)).toBe("Hoje, domingo")
    expect(formatRelativeSessionDay("2026-10-05", zone, now)).toBe("Amanhã, segunda-feira")
    expect(formatRelativeSessionDay("2026-10-08", zone, now)).toBe("Quinta-feira, 8 de outubro")
  })
})

describe("pickRecentSessions", () => {
  const now = new Date("2026-10-15T15:00:00Z")

  it("returns past sessions newest first, without the next one or future ones", () => {
    const list = [
      daily(1, "2026-10-01"),
      daily(2, "2026-10-08"),
      daily(3, "2026-10-15", "SCHEDULED"),
      daily(4, "2026-10-22", "SCHEDULED"),
      daily(5, "2026-09-24"),
    ]
    expect(pickRecentSessions(list, 3, 3, zone, now).map((d) => d.id)).toEqual([2, 1, 5])
  })

  it("is empty when there is nothing before today", () => {
    expect(pickRecentSessions([daily(4, "2026-10-22", "SCHEDULED")], null, 3, zone, now)).toEqual([])
  })
})

// The same instant has different calendar dates across peladas, independently of the browser zone.
describe("session calendar boundaries", () => {
  const instant = new Date("2026-11-15T01:00:00Z")
  const list = [daily(1, "2026-11-14", "SCHEDULED"), daily(2, "2026-11-15", "CONFIRMED")]

  it("keeps today's session west of UTC and skips yesterday east of UTC", () => {
    expect(pickNextSession(list, "America/Sao_Paulo", instant)?.id).toBe(1)
    expect(pickNextSession(list, "Pacific/Kiritimati", instant)?.id).toBe(2)
  })

  it("limits recent activity to the pelada's current day", () => {
    expect(pickRecentSessions(list, null, 3, "America/Sao_Paulo", instant).map(d => d.id)).toEqual([1])
    expect(pickRecentSessions(list, null, 3, "Pacific/Kiritimati", instant).map(d => d.id)).toEqual([2, 1])
  })

  it("labels today and tomorrow using the pelada's calendar", () => {
    expect(formatRelativeSessionDay("2026-11-14", "America/Sao_Paulo", instant)).toBe("Hoje, sábado")
    expect(formatRelativeSessionDay("2026-11-15", "America/Sao_Paulo", instant)).toBe("Amanhã, domingo")
    expect(formatRelativeSessionDay("2026-11-15", "Pacific/Kiritimati", instant)).toBe("Hoje, domingo")
    expect(formatRelativeSessionDay("2026-01-01", "America/Sao_Paulo", new Date("2026-01-01T01:00:00Z"))).toBe("Amanhã, quinta-feira")
  })
})
