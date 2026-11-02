import { describe, expect, it } from "vitest"
import css from "@/index.css?raw"
import { CONTRAST_PAIRS, contrastRatio, hslTripletToRgb, parseThemeTokens, resolveToken, tokenContrast } from "@/lib/contrast"

describe("contrast helpers", () => {
  it("converts HSL triplets", () => {
    expect(hslTripletToRgb("0 0% 100%")).toEqual([255, 255, 255])
    expect(hslTripletToRgb("197 94% 43.1%").map(Math.round)).toEqual([7, 155, 213]) // #0799D5 (the triplet is rounded)
  })

  it("matches the WCAG reference ratios", () => {
    expect(contrastRatio([255, 255, 255], [0, 0, 0])).toBeCloseTo(21, 5)
    // white on Electric Blue: why brand fills carry Midnight text
    expect(contrastRatio([255, 255, 255], [7, 153, 213])).toBeCloseTo(3.22, 2)
  })

  it("resolves references and inherits light tokens in dark", () => {
    const tokens = parseThemeTokens(":root { --a: 0 0% 0%; --b: var(--a); --c: 1 1% 1%; } .dark { --b: var(--c); }")
    expect(resolveToken(tokens.light, "b")).toBe("0 0% 0%")
    expect(resolveToken(tokens.dark, "b")).toBe("1 1% 1%")
    expect(resolveToken(tokens.dark, "a")).toBe("0 0% 0%")
  })
})

const themes = parseThemeTokens(css)

describe.each(["light", "dark"] as const)("%s theme tokens", (theme) => {
  it.each(CONTRAST_PAIRS)("%s on %s ≥ %s:1", (foreground, background, minimum) => {
    expect(tokenContrast(themes[theme], foreground, background)).toBeGreaterThanOrEqual(minimum)
  })
})
