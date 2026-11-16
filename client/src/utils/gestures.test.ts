import { describe, expect, it } from "vitest"
import { completesBack, PULL_MAX, pullDistance, pullFires, startsAtEdge, swipeIntent, velocity } from "@/utils/gestures"

describe("swipe back", () => {
  it("only starts at the left edge", () => {
    expect(startsAtEdge(0)).toBe(true)
    expect(startsAtEdge(24)).toBe(true)
    expect(startsAtEdge(25)).toBe(false)
  })

  it("waits 10 px before deciding the direction", () => {
    expect(swipeIntent(6, 3)).toBeNull()
    expect(swipeIntent(12, 4)).toBe("horizontal")
    expect(swipeIntent(4, 12)).toBe("vertical")
    expect(swipeIntent(-12, 3)).toBe("horizontal")
  })

  it("completes past 35% of the width or above 0.5 px/ms", () => {
    expect(completesBack(140, 390, 0.1)).toBe(true)
    expect(completesBack(130, 390, 0.1)).toBe(false)
    expect(completesBack(40, 390, velocity(40, 60))).toBe(true)
    expect(completesBack(-200, 390, 2)).toBe(false)
  })

  it("has no velocity without elapsed time", () => {
    expect(velocity(100, 0)).toBe(0)
  })
})

describe("pull to refresh", () => {
  it("halves the finger's distance and caps it", () => {
    expect(pullDistance(100)).toBe(50)
    expect(pullDistance(-30)).toBe(0)
    expect(pullDistance(1000)).toBe(PULL_MAX)
  })

  it("fires from 64 px", () => {
    expect(pullFires(pullDistance(127))).toBe(false)
    expect(pullFires(pullDistance(128))).toBe(true)
  })
})
