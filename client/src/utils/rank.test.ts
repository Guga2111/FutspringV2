import { describe, expect, it } from "vitest"
import { rankStyle } from "@/utils/rank"

describe("rankStyle", () => {
  it("shows the podium as ordinals with medal colors", () => {
    expect(rankStyle(1)).toEqual({ label: "1º", className: "font-bold text-gold" })
    expect(rankStyle(2).className).toContain("text-silver")
    expect(rankStyle(3)).toEqual({ label: "3º", className: "font-bold text-bronze" })
  })

  it("shows the other positions as plain numbers", () => {
    expect(rankStyle(4)).toEqual({ label: "4", className: "text-subtle-foreground" })
  })

  it("drops the podium when the positions earned nothing", () => {
    expect(rankStyle(1, false)).toEqual({ label: "1", className: "text-subtle-foreground" })
  })
})
