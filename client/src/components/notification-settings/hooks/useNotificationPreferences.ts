import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"
import { getNotificationPreferences, updateNotificationPreferences } from "@/api/notifications"
import { getErrorMessage } from "@/lib/errors"
import { notificationCategoryLabel } from "@/types/notification"
import type { NotificationCategory, NotificationChannel, NotificationPreference } from "@/types/notification"

interface LoadedResult {
  requestKey: number
  data: NotificationPreference[] | null
  error: boolean
}

type ChannelValues = Partial<Record<NotificationCategory, Partial<Record<NotificationChannel, boolean>>>>

export interface NotificationPreferencesState {
  // per category and channel, with the switches being saved already applied (optimistic)
  enabled: ChannelValues
  saving: ChannelValues
  loading: boolean
  error: boolean
  retry: () => void
  toggle: (category: NotificationCategory, channel: NotificationChannel, next: boolean) => Promise<void>
}

const CHANNEL_FIELD = { email: "emailEnabled", push: "pushEnabled" } as const satisfies Record<NotificationChannel, keyof NotificationPreference>

function toastText(category: NotificationCategory, channel: NotificationChannel, next: boolean): string {
  const label = notificationCategoryLabel[category]
  return channel === "email"
    ? `E-mails de ${label} ${next ? "ativados" : "desativados"}`
    : `Push de ${label} ${next ? "ativado" : "desativado"}`
}

// The caller's preferences, e-mail and push per category. A switch is saved as soon as it changes (only that
// category and channel go in the PUT) and goes back on error, like the pelada's Automação card.
// Results are keyed by request, so a retry never shows the previous error.
export function useNotificationPreferences(): NotificationPreferencesState {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<LoadedResult | null>(null)
  const [pending, setPending] = useState<ChannelValues>({})

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

  const toggle = useCallback(async (category: NotificationCategory, channel: NotificationChannel, next: boolean) => {
    setPending((prev) => ({ ...prev, [category]: { ...prev[category], [channel]: next } }))
    try {
      const saved = await updateNotificationPreferences([{ category, [CHANNEL_FIELD[channel]]: next }])
      setResult((prev) => (prev ? { ...prev, data: saved } : prev))
      toast.success(toastText(category, channel, next))
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível salvar a preferência"))
    } finally {
      setPending((prev) => {
        const rest = { ...prev[category] }
        delete rest[channel]
        return { ...prev, [category]: rest }
      })
    }
  }, [])

  const current = result?.requestKey === attempt ? result : null
  const enabled: ChannelValues = {}
  current?.data?.forEach((p) => {
    enabled[p.category] = { email: p.emailEnabled, push: p.pushEnabled, ...pending[p.category] }
  })
  const saving: ChannelValues = {}
  for (const [category, channels] of Object.entries(pending) as [NotificationCategory, Partial<Record<NotificationChannel, boolean>>][]) {
    saving[category] = Object.fromEntries(Object.keys(channels).map((c) => [c, true]))
  }

  return { enabled, saving, loading: current === null, error: current?.error ?? false, retry, toggle }
}
