import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { applyThemePreference } from "@/hooks/useTheme"

// Node environment: a minimal <html> class list, storage and media query
function stubBrowser(systemDark: boolean) {
  const classes = new Set<string>()
  const storage = new Map<string, string>()
  vi.stubGlobal("document", {
    documentElement: {
      classList: {
        contains: (name: string) => classes.has(name),
        toggle: (name: string, force: boolean) => (force ? classes.add(name) : classes.delete(name), force),
      },
    },
  })
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  })
  vi.stubGlobal("window", { matchMedia: () => ({ matches: systemDark }) })
  return { classes, storage }
}

describe("applyThemePreference", () => {
  afterEach(() => vi.unstubAllGlobals())

  describe("on a light system", () => {
    let browser: ReturnType<typeof stubBrowser>
    beforeEach(() => {
      browser = stubBrowser(false)
    })

    it("dark saves the choice and applies the class", () => {
      applyThemePreference("dark")
      expect(browser.storage.get("theme")).toBe("dark")
      expect(browser.classes.has("dark")).toBe(true)
    })

    it("light saves the choice and removes the class", () => {
      browser.classes.add("dark")
      applyThemePreference("light")
      expect(browser.storage.get("theme")).toBe("light")
      expect(browser.classes.has("dark")).toBe(false)
    })

    it("system forgets the choice and follows the OS", () => {
      applyThemePreference("dark")
      applyThemePreference("system")
      expect(browser.storage.has("theme")).toBe(false)
      expect(browser.classes.has("dark")).toBe(false)
    })
  })

  it("system on a dark OS applies the dark class", () => {
    const browser = stubBrowser(true)
    applyThemePreference("system")
    expect(browser.storage.has("theme")).toBe(false)
    expect(browser.classes.has("dark")).toBe(true)
  })
})
