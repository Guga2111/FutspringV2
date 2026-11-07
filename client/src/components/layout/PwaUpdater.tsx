import { useEffect } from "react"
import { toast } from "sonner"
import { useRegisterSW } from "virtual:pwa-register/react"

// Registers the service worker and, when a new build is waiting, offers it in a toast that stays
// until it is used: activating on its own would reload the page in the middle of a form.
// Mounted in main.tsx, so it also runs on the public pages. The service worker is off in `bun run dev`.
export function PwaUpdater() {
  const {
    needRefresh: [needRefresh],
    updateServiceWorker,
  } = useRegisterSW()

  useEffect(() => {
    if (!needRefresh) return
    toast("Nova versão disponível", {
      id: "pwa-update",
      duration: Infinity,
      action: { label: "Atualizar", onClick: () => void updateServiceWorker(true) },
    })
  }, [needRefresh, updateServiceWorker])

  return null
}
