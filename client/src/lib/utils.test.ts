import { describe, expect, it } from "vitest"
import { getAvatarColor, getFileUrl, getInitials } from "./utils"
import { dayOfWeekLabel } from "./constants"

describe("getInitials", () => {
  it("uses the first two letters of a single word", () => {
    expect(getInitials("leal")).toBe("LE")
  })

  it("uses first and last word initials", () => {
    expect(getInitials("Pelada do Fut")).toBe("PF")
  })

  it("handles blank names", () => {
    expect(getInitials("   ")).toBe("?")
  })
})

describe("getFileUrl", () => {
  it("builds the files URL and ignores empty names", () => {
    expect(getFileUrl("a.png")).toMatch(/\/api\/v1\/files\/a\.png$/)
    expect(getFileUrl(null)).toBeUndefined()
  })
})

describe("dayOfWeekLabel", () => {
  it("translates API values and keeps unknown ones", () => {
    expect(dayOfWeekLabel("SATURDAY")).toBe("Sábado")
    expect(dayOfWeekLabel("Sabado")).toBe("Sabado")
  })
})

describe("getAvatarColor", () => {
  it("cycles through the six avatar tokens by id", () => {
    expect(getAvatarColor(1)).toBe("bg-avatar-2")
    expect(getAvatarColor(6)).toBe("bg-avatar-1")
    expect(getAvatarColor(7)).toBe(getAvatarColor(1))
  })
})
