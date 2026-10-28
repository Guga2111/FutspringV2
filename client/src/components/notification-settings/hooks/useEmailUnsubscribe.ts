import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"
import { resubscribeToEmails, unsubscribeFromEmails } from "@/api/notifications"
import { getErrorMessage } from "@/lib/errors"
import type { NotificationPreference } from "@/types/notification"

interface Result {
  token: string
  preference: NotificationPreference | null
  // the API message ("Link inválido") when the first request failed
  error: string | null
}

export interface EmailUnsubscribeState {
  // the category and whether its e-mails are on now; null while loading or on error
  preference: NotificationPreference | null
  loading: boolean
  error: string | null
  pending: boolean
  resubscribe: () => Promise<void>
  unsubscribe: () => Promise<void>
}

// The e-mail's "Desativar" link: POSTs the token as soon as the page opens (a GET never changes anything, since
// mail scanners open links). Idempotent, so StrictMode's second effect run is harmless. Works logged out.
export function useEmailUnsubscribe(token: string): EmailUnsubscribeState {
  const [result, setResult] = useState<Result | null>(null)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    let cancelled = false
    unsubscribeFromEmails(token)
      .then((preference) => {
        if (!cancelled) setResult({ token, preference, error: null })
      })
      .catch((error: unknown) => {
        if (!cancelled) setResult({ token, preference: null, error: getErrorMessage(error, "Link inválido") })
      })
    return () => {
      cancelled = true
    }
  }, [token])

  const change = useCallback(
    async (request: (token: string) => Promise<NotificationPreference>) => {
      setPending(true)
      try {
        const preference = await request(token)
        setResult({ token, preference, error: null })
      } catch (error) {
        toast.error(getErrorMessage(error, "Não foi possível salvar a preferência"))
      } finally {
        setPending(false)
      }
    },
    [token],
  )

  const resubscribe = useCallback(() => change(resubscribeToEmails), [change])
  const unsubscribe = useCallback(() => change(unsubscribeFromEmails), [change])

  const current = result?.token === token ? result : null
  return {
    preference: current?.preference ?? null,
    loading: current === null,
    error: current?.error ?? null,
    pending,
    resubscribe,
    unsubscribe,
  }
}
