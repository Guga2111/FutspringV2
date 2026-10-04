import { describe, it, expect } from "vitest"
import { countByPosition, filterMembers, normalizePosition } from "./memberFilters"
import type { PeladaMember } from "@/types/pelada"

const member = (id: number, username: string, position: string | null): PeladaMember => ({
  id,
  username,
  position,
  image: null,
  stars: 3,
  isAdmin: false,
})

const members = [
  member(1, "Leal", "ATACANTE"),
  member(2, "Nando", "GOALKEEPER"),
  member(3, "Lui", "MEIO"),
  member(4, "Souto", "FORWARD"),
  member(5, "Sem Posição", null),
]

describe("normalizePosition", () => {
  it("maps English and Portuguese values to the four filters", () => {
    expect(normalizePosition("GOALKEEPER")).toBe("GOLEIRO")
    expect(normalizePosition("MIDFIELDER")).toBe("MEIO")
    expect(normalizePosition("ATACANTE")).toBe("ATACANTE")
    expect(normalizePosition(null)).toBeNull()
    expect(normalizePosition("")).toBeNull()
  })
})

describe("countByPosition", () => {
  it("counts every member in ALL and each normalized position", () => {
    expect(countByPosition(members)).toEqual({ ALL: 5, GOLEIRO: 1, ZAGUEIRO: 0, MEIO: 1, ATACANTE: 2 })
  })
})

describe("filterMembers", () => {
  it("filters by position and case-insensitive name", () => {
    expect(filterMembers(members, "", "ATACANTE").map((m) => m.id)).toEqual([1, 4])
    expect(filterMembers(members, "  le ", "ALL").map((m) => m.id)).toEqual([1])
    expect(filterMembers(members, "x", "ALL")).toEqual([])
  })

  it("sorts by name", () => {
    expect(filterMembers(members, "", "ALL").map((m) => m.username)).toEqual(["Leal", "Lui", "Nando", "Sem Posição", "Souto"])
  })

  it("keeps members without a position only in ALL", () => {
    expect(filterMembers(members, "sem", "ALL")).toHaveLength(1)
    expect(filterMembers(members, "sem", "MEIO")).toHaveLength(0)
  })
})
