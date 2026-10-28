import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"
import { getNotificationPreferences, updateNotificationPreferences } from "@/api/notifications"
import { getErrorMessage } from "@/lib/errors"
import { notificationCategoryLabel } from "@/types/notification"
import type { NotificationCategory, NotificationPreference } from "@/types/notification"

interface LoadedResult {
  requestKey: number
  data: NotificationPreference[] | null
  error: boolean
}

export interface NotificationPreferencesState {
  // e-mail on per category, with the switches being saved already applied (optimistic)
  emailEnabled: Partial<Record<NotificationCategory, boolean>>
  saving: Partial<Record<NotificationCategory, boolean>>
  loading: boolean
  error: boolean
  retry: () => void
  toggle: (category: NotificationCategory, next: boolean) => Promise<void>
}

// The caller's e-mail preferences. A switch is saved as soon as it changes and goes back on error, like the
// pelada's Automação card. Results are keyed by request, so a retry never shows the previous error.
export function useNotificationPreferences(): NotificationPreferencesState {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<LoadedResult | null>(null)
  const [pending, setPending] = useState<Partial<Record<NotificationCategory, boolean>>>({})

  useEffect(() => {
    let cancelled = false
    getNotificationPreferences()
      .then((data) => {
        if (!cancelled) setResult({ requestKey: attempt, data, error: false })
      })
      .catch(() => {
        if (!cancelled) setResult({ requestKey: attempt, data: null, error: true })
      })
    return () => {
      cancelled = true
    }
  }, [attempt])

  const retry = useCallback(() => setAttempt((n) => n + 1), [])

  const toggle = useCallback(async (category: NotificationCategory, next: boolean) => {
    setPending((prev) => ({ ...prev, [category]: next }))
    try {
      const saved = await updateNotificationPreferences([{ category, emailEnabled: next }])
      setResult((prev) => (prev ? { ...prev, data: saved } : prev))
      toast.success(`E-mails de ${notificationCategoryLabel[category]} ${next ? "ativados" : "desativados"}`)
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível salvar a preferência"))
    } finally {
      setPending((prev) => {
        const rest = { ...prev }
        delete rest[category]
        return rest
      })
    }
  }, [])

  const current = result?.requestKey === attempt ? result : null
  const emailEnabled: Partial<Record<NotificationCategory, boolean>> = {}
  current?.data?.forEach((p) => {
    emailEnabled[p.category] = p.emailEnabled
  })

  return {
    emailEnabled: { ...emailEnabled, ...pending },
    saving: Object.fromEntries(Object.keys(pending).map((c) => [c, true])),
    loading: current === null,
    error: current?.error ?? false,
    retry,
    toggle,
  }
}
