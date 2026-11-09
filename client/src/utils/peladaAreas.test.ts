import { describe, expect, it } from "vitest"
import { areaFromPath, areaPath, isPeladaPath, peladaRole, visibleAreas } from "@/utils/peladaAreas"

describe("peladaAreas", () => {
  it("builds the path of each area", () => {
    expect(areaPath(7, "overview")).toBe("/pelada/7")
    expect(areaPath(7, "members")).toBe("/pelada/7/membros")
    expect(areaPath("7", "settings")).toBe("/pelada/7/settings")
  })

  it("reads the area from a pathname", () => {
    expect(areaFromPath("/pelada/7")).toBe("overview")
    expect(areaFromPath("/pelada/7/")).toBe("overview")
    expect(areaFromPath("/pelada/7/sessoes")).toBe("sessions")
    expect(areaFromPath("/pelada/7/financeiro")).toBe("finance")
    expect(areaFromPath("/pelada/7/settings")).toBe("settings")
    expect(areaFromPath("/pelada/7/assinatura")).toBe("subscription")
    expect(areaFromPath("/pelada/7/xyz")).toBeNull()
    expect(areaFromPath("/daily/3")).toBeNull()
    expect(areaFromPath("/pelada/7/financeiro/42")).toBe("finance")
    expect(areaFromPath("/pelada/7/financeiro/42/")).toBe("finance")
    expect(areaFromPath("/pelada/7/financeiro/abc")).toBeNull()
    expect(areaFromPath("/pelada/7/membros/3")).toBeNull()
  })

  it("shows each role its areas: admin ones to admins, Assinatura to the creator only", () => {
    expect(visibleAreas("member")).toEqual(["overview", "members", "sessions", "ranking", "awards", "finance"])
    expect(visibleAreas("admin")).toEqual([
      "overview", "members", "sessions", "ranking", "awards", "finance", "invitations", "settings",
    ])
    expect(visibleAreas("creator")).toEqual([
      "overview", "members", "sessions", "ranking", "awards", "finance", "invitations", "settings", "subscription",
    ])
  })

  it("derives the role, the creator first", () => {
    expect(peladaRole(true, true)).toBe("creator")
    expect(peladaRole(true, false)).toBe("admin")
    expect(peladaRole(false, false)).toBe("member")
  })

  it("matches the pelada and its descendant routes only", () => {
    expect(isPeladaPath("/pelada/7", 7)).toBe(true)
    expect(isPeladaPath("/pelada/7/ranking", 7)).toBe(true)
    expect(isPeladaPath("/pelada/7/financeiro/42", 7)).toBe(true)
    expect(isPeladaPath("/pelada/70", 7)).toBe(false)
    expect(isPeladaPath("/home", 7)).toBe(false)
  })
})
