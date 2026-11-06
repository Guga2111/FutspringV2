import { useCallback, useSyncExternalStore } from "react"

// "system" = nothing saved: the inline script in index.html follows prefers-color-scheme live
export type ThemePreference = "light" | "dark" | "system"

const STORAGE_KEY = "theme"
const listeners = new Set<() => void>()

// The theme is the `dark` class on <html>; the preference is localStorage.theme (per device)
function subscribe(onChange: () => void) {
  const observer = new MutationObserver(onChange)
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] })
  listeners.add(onChange)
  return () => {
    observer.disconnect()
    listeners.delete(onChange)
  }
}

const isDarkNow = () => document.documentElement.classList.contains("dark")

function readPreference(): ThemePreference {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved === "light" || saved === "dark" ? saved : "system"
  } catch {
    return "system"
  }
}

function systemIsDark() {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-color-scheme: dark)").matches
}

export function applyThemePreference(preference: ThemePreference) {
  try {
    if (preference === "system") localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, preference)
  } catch {
    // storage unavailable (private mode): the choice lasts for this page only
  }
  document.documentElement.classList.toggle("dark", preference === "system" ? systemIsDark() : preference === "dark")
  listeners.forEach((listener) => listener())
}

export function useTheme() {
  const isDark = useSyncExternalStore(subscribe, isDarkNow, () => false)
  const preference = useSyncExternalStore(subscribe, readPreference, () => "system" as const)

  const setPreference = useCallback((next: ThemePreference) => applyThemePreference(next), [])
  // the user menu's shortcut: saves the opposite of what is on screen
  const toggleTheme = useCallback(() => applyThemePreference(isDarkNow() ? "light" : "dark"), [])

  return { isDark, preference, setPreference, toggleTheme }
}
