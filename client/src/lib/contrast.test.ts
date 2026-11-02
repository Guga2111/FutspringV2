import { describe, expect, it } from "vitest"
import css from "@/index.css?raw"
import { contrastRatio, hslTripletToRgb, parseThemeTokens, resolveToken, tokenContrast } from "@/lib/contrast"

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

// [foreground, background, minimum]: 4.5 for text, 3 for non-text (borders, rings, icons)
const TEXT = 4.5
const UI = 3
const pairs: [string, string, number][] = [
  ["foreground", "background", TEXT],
  ["card-foreground", "card", TEXT],
  ["popover-foreground", "popover", TEXT],
  ["foreground", "card-elevated", TEXT],
  ["foreground", "row-hover", TEXT],
  ...["background", "card", "card-elevated", "muted", "row-hover"].flatMap((bg): [string, string, number][] => [
    ["muted-foreground", bg, TEXT],
    ["subtle-foreground", bg, TEXT],
    ["faint-foreground", bg, TEXT],
  ]),
  ["primary-foreground", "primary", TEXT],
  ["secondary-foreground", "secondary", TEXT],
  ["accent-foreground", "accent", TEXT],
  ["brand-foreground", "brand", TEXT],
  ["brand-foreground", "brand-hover", TEXT],
  ["brand-text", "background", TEXT],
  ["brand-text", "card", TEXT],
  ["brand-text", "card-elevated", TEXT],
  ["brand-text", "brand-muted", TEXT],
  ["highlight-foreground", "highlight", TEXT],
  ["highlight-text", "background", TEXT],
  ["highlight-text", "card", TEXT],
  ["highlight-text", "highlight-muted", TEXT],
  ["info", "info-muted", TEXT],
  ["info", "card", TEXT],
  ["success", "card", TEXT],
  ["success-soft", "success-muted", TEXT],
  ["warning", "card", TEXT],
  ["destructive-soft", "card", TEXT],
  ["destructive-soft", "destructive-muted", TEXT],
  ["status-scheduled-foreground", "status-scheduled", TEXT],
  ["status-live-foreground", "status-live", TEXT],
  ["live-foreground", "live-from", TEXT],
  ["live-foreground", "live-to", TEXT],
  ["sidebar-foreground", "sidebar-background", TEXT],
  ["sidebar-primary-foreground", "sidebar-primary", TEXT],
  ["sidebar-accent-foreground", "sidebar-accent", TEXT],
  // initials on colored avatars are white in both themes
  ...[1, 2, 3, 4, 5, 6].map((n): [string, string, number] => ["p-white", `avatar-${n}`, TEXT]),
  ["input", "background", UI],
  ["input", "card", UI],
  ["ring", "background", UI],
  ["ring", "card", UI],
  ["brand", "card", UI],
  // stars and medals; the Cantaloupe fill only carries text, so icons use highlight-text
  ["gold", "card", UI],
]

const themes = parseThemeTokens(css)

describe.each(["light", "dark"] as const)("%s theme tokens", (theme) => {
  it.each(pairs)("%s on %s ≥ %s:1", (foreground, background, minimum) => {
    expect(tokenContrast(themes[theme], foreground, background)).toBeGreaterThanOrEqual(minimum)
  })
})
