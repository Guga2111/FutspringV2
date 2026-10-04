import { useCallback, useSyncExternalStore } from "react"

// The theme is the `dark` class on <html> (applied from localStorage.theme in main.tsx)
function subscribe(onChange: () => void) {
  const observer = new MutationObserver(onChange)
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] })
  return () => observer.disconnect()
}

const isDarkNow = () => document.documentElement.classList.contains("dark")

export function useTheme() {
  const isDark = useSyncExternalStore(subscribe, isDarkNow, () => false)

  const toggleTheme = useCallback(() => {
    const nowDark = document.documentElement.classList.toggle("dark")
    try {
      localStorage.setItem("theme", nowDark ? "dark" : "light")
    } catch {
      // storage unavailable (private mode): the choice lasts for this page only
    }
  }, [])

  return { isDark, toggleTheme }
}
