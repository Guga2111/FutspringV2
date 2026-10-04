import { describe, it, expect } from "vitest"
import { formatCardDate, greeting, pendingLine, statsKpis, upcomingSessions } from "./home"
import type { PeladaResponse } from "@/types/pelada"
import type { StatsDTO } from "@/types/stats"

describe("greeting", () => {
  it("changes with the hour", () => {
    expect(greeting(new Date(2026, 9, 4, 8))).toBe("Bom dia")
    expect(greeting(new Date(2026, 9, 4, 13))).toBe("Boa tarde")
    expect(greeting(new Date(2026, 9, 4, 21))).toBe("Boa noite")
  })
})

describe("formatCardDate", () => {
  it("uses short weekday, day and month", () => {
    expect(formatCardDate("2026-10-08")).toBe("Qui, 08 out")
  })
})

describe("upcomingSessions", () => {
  const pelada = (id: number, date: string | null): PeladaResponse =>
    ({
      id,
      nextDaily: date ? { id: id * 10, date, time: "20:00", status: "SCHEDULED", confirmedCount: 0, capacity: 10, isConfirmed: false } : null,
    }) as PeladaResponse

  it("keeps peladas with a next session, earliest first", () => {
    expect(upcomingSessions([pelada(1, "2026-10-11"), pelada(2, null), pelada(3, "2026-10-08")]).map((p) => p.id)).toEqual([3, 1])
  })
})

describe("pendingLine", () => {
  it("pluralizes", () => {
    expect(pendingLine(0)).toBe("Presença confirmada em todas as próximas sessões.")
    expect(pendingLine(1)).toBe("Você tem 1 sessão esperando sua confirmação.")
    expect(pendingLine(2)).toBe("Você tem 2 sessões esperando sua confirmação.")
  })
})

describe("statsKpis", () => {
  it("computes per-match averages and win rate", () => {
    const stats = { goals: 23, assists: 11, matchesPlayed: 58, matchWins: 31, sessionsPlayed: 9 } as StatsDTO
    expect(statsKpis(stats).map((k) => k.sub)).toEqual([
      "0,40 por partida",
      "0,19 por partida",
      "em 9 sessões",
      "53% de aproveitamento",
    ])
  })

  it("handles a player without matches", () => {
    const stats = { goals: 0, assists: 0, matchesPlayed: 0, matchWins: 0, sessionsPlayed: 0 } as StatsDTO
    expect(statsKpis(stats)[3].sub).toBe("0% de aproveitamento")
  })
})
