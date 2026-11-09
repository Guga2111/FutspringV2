import { useEffect } from "react"
import { useLocation } from "react-router-dom"

// Settings pages: a link such as /settings#notificacoes opens with that card at the top, once its content is
// on screen (`ready`). The SettingsNav itself scrolls without changing the hash.
export function useScrollToHash(ready: boolean) {
  const { hash } = useLocation()
  useEffect(() => {
    if (!ready || !hash) return
    const frame = requestAnimationFrame(() => {
      document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView({ block: "start" })
    })
    return () => cancelAnimationFrame(frame)
  }, [ready, hash])
}
