import { describe, it, expect } from "vitest"
import { attendanceHint, canSortTeams, getSelfConfirmState, sortByConfirmation, sortTeamsHint, splitAttendance } from "./attendance"
import type { PlayerDTO } from "@/types/daily"

const p = (id: number, username: string): PlayerDTO => ({ id, username, image: null, stars: 3, position: null })

describe("splitAttendance", () => {
  it("splits members into confirmed and pending, sorted by name", () => {
    const members = [p(1, "Tuca"), p(2, "leal"), p(3, "Abreu")]
    const result = splitAttendance(members, [p(1, "Tuca"), p(4, "Ex-membro")])
    expect(result.confirmed.map((m) => m.username)).toEqual(["Ex-membro", "Tuca"])
    expect(result.pending.map((m) => m.username)).toEqual(["Abreu", "leal"])
  })
})

describe("canSortTeams", () => {
  it("needs exactly teams × players", () => {
    expect(canSortTeams(20, 4, 5)).toBe(true)
    expect(canSortTeams(19, 4, 5)).toBe(false)
    expect(canSortTeams(21, 4, 5)).toBe(false)
  })
})

describe("attendanceHint", () => {
  it("says how many are missing, complete or extra", () => {
    expect(attendanceHint(14, 4, 5)).toBe("Faltam 6 para fechar 4 times de 5.")
    expect(attendanceHint(20, 4, 5)).toBe("Lista completa. Os times já podem ser sorteados.")
    expect(attendanceHint(21, 4, 5)).toBe("1 confirmado a mais para fechar 4 times de 5.")
  })
})

describe("sortTeamsHint", () => {
  it("explains the sort rule", () => {
    expect(sortTeamsHint(14, 4, 5)).toBe("É preciso ter exatamente 20 confirmados (4 × 5) para sortear. Hoje são 14.")
    expect(sortTeamsHint(20, 4, 5)).toBe("Lista fechada. Sorteie para equilibrar os times pelas estrelas.")
  })
})

describe("getSelfConfirmState", () => {
  const opensAt = "2026-10-29T23:00:00Z"
  const base = { isConfirmed: false, confirmOpensAt: opensAt, confirmedCount: 3, capacity: 10 }
  const at = (iso: string) => new Date(iso)

  it("is confirmed whatever the window or the capacity", () => {
    expect(getSelfConfirmState({ ...base, isConfirmed: true, confirmedCount: 10 }, at("2026-10-01T00:00:00Z"))).toBe("confirmed")
  })

  it("keeps a diarista waiting until the exact opening instant", () => {
    expect(getSelfConfirmState(base, at("2026-10-29T22:59:59Z"))).toBe("notYet")
    expect(getSelfConfirmState(base, at(opensAt))).toBe("open")
  })

  it("a full session is closed to diaristas and a priority for mensalistas", () => {
    const full = { ...base, confirmedCount: 10 }
    expect(getSelfConfirmState(full, at("2026-10-30T12:00:00Z"))).toBe("full")
    expect(getSelfConfirmState({ ...full, confirmOpensAt: null }, at("2026-10-01T00:00:00Z"))).toBe("priority")
  })

  it("mensalistas have no window", () => {
    expect(getSelfConfirmState({ ...base, confirmOpensAt: null }, at("2026-10-01T00:00:00Z"))).toBe("open")
  })
})

describe("sortByConfirmation", () => {
  it("orders by confirmedAt, then id; players without it go last by name", () => {
    const p = (id: number, username: string, confirmedAt?: string) =>
      ({ id, username, image: null, stars: 3, position: null, confirmedAt })
    const sorted = sortByConfirmation([
      p(3, "Caio"),
      p(2, "Bia", "2026-10-30T12:00:00Z"),
      p(1, "Ana", "2026-10-30T12:00:00Z"),
      p(4, "Duda", "2026-10-29T12:00:00Z"),
      p(5, "Alê"),
    ])
    expect(sorted.map((x) => x.id)).toEqual([4, 1, 2, 5, 3])
  })
})
