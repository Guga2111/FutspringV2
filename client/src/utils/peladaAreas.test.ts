import { describe, expect, it } from "vitest"
import { areaFromPath, areaPath, isPeladaPath, visibleAreas } from "@/utils/peladaAreas"

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
    expect(areaFromPath("/pelada/7/xyz")).toBeNull()
    expect(areaFromPath("/daily/3")).toBeNull()
  })

  it("hides admin-only areas from members", () => {
    expect(visibleAreas(false)).not.toContain("invitations")
    expect(visibleAreas(false)).not.toContain("settings")
    expect(visibleAreas(false)).toContain("finance")
    expect(visibleAreas(true)).toEqual([
      "overview", "members", "sessions", "ranking", "awards", "finance", "invitations", "settings",
    ])
  })

  it("matches the pelada and its descendant routes only", () => {
    expect(isPeladaPath("/pelada/7", 7)).toBe(true)
    expect(isPeladaPath("/pelada/7/ranking", 7)).toBe(true)
    expect(isPeladaPath("/pelada/70", 7)).toBe(false)
    expect(isPeladaPath("/home", 7)).toBe(false)
  })
})
