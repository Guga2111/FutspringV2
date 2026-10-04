import { describe, it, expect } from "vitest"
import { attendanceHint, canSortTeams, sortTeamsHint, splitAttendance } from "./attendance"
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
