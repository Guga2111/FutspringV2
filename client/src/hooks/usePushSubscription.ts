import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"
import { getVapidPublicKey, subscribePush, unsubscribePush } from "@/api/push"
import { usePwaInstall } from "@/hooks/usePwaInstall"
import { getErrorMessage } from "@/lib/errors"
import { base64UrlToBytes, currentPushSubscription, pushRegistration, pushSupported, sameServerKey, subscriptionRequest } from "@/lib/push"
import type { PushStatus } from "@/types/push"
import { installPlatform } from "@/utils/pwaPlatform"

// The server's VAPID key, read once per page load (a failure is retried on the next read)
let vapidKey: Promise<string | null> | null = null
function loadVapidKey(): Promise<string | null> {
  vapidKey ??= getVapidPublicKey().catch((error) => {
    vapidKey = null
    throw error
  })
  return vapidKey
}

async function subscribeDevice(registration: ServiceWorkerRegistration, key: string): Promise<PushSubscription> {
  const subscription = await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(key) })
  const body = subscriptionRequest(subscription.toJSON())
  try {
    if (!body) throw new Error("subscription without keys")
    await subscribePush(body)
  } catch (error) {
    // not stored on the server: don't leave the device looking active
    await subscription.unsubscribe().catch(() => undefined)
    throw error
  }
  return subscription
}

// Called by AppLayout when the app opens for a user: sends this device's subscription again (an idempotent upsert),
// which covers an endpoint the browser renewed, a subscription the server dropped (a password change deletes them)
// and a new VAPID key (subscribed again with it). Needs nothing from the user: the permission is already granted.
export async function syncPushSubscription(): Promise<void> {
  if (!pushSupported() || Notification.permission !== "granted") return
  const registration = await pushRegistration()
  const subscription = await registration?.pushManager.getSubscription()
  if (!registration || !subscription) return
  const key = await loadVapidKey()
  if (!key) return
  if (sameServerKey(subscription.options.applicationServerKey, key)) {
    const body = subscriptionRequest(subscription.toJSON())
    if (body) await subscribePush(body)
    return
  }
  await subscription.unsubscribe()
  await subscribeDevice(registration, key)
}

// What doesn't need a request: an iPhone outside the installed app has no PushManager at all
function deviceStatus(needsInstall: boolean): PushStatus | null {
  if (needsInstall) return "needsInstall"
  if (!pushSupported()) return "unsupported"
  if (Notification.permission === "denied") return "denied"
  return null
}

async function readStatus(): Promise<PushStatus> {
  const key = await loadVapidKey()
  if (!key) return "unconfigured"
  const subscription = await currentPushSubscription()
  return subscription && Notification.permission === "granted" ? "active" : "inactive"
}

export interface PushSubscriptionState {
  // null while it is being read
  status: PushStatus | null
  pending: boolean
  enable: () => Promise<void>
  disable: () => Promise<void>
}

// Web Push on this device for the logged-in account. `enable` must run from a click: iOS only asks for the
// permission inside a user gesture, so it is the first thing it does. Re-read when the tab becomes visible again
// (the permission may have changed in the phone's settings); the previous status stays on screen while re-reading.
export function usePushSubscription(): PushSubscriptionState {
  const { context } = usePwaInstall()
  const needsInstall = context !== "installed" && installPlatform(context, navigator.userAgent) === "ios"
  const [attempt, setAttempt] = useState(0)
  const [read, setRead] = useState<PushStatus | null>(null)
  const [pending, setPending] = useState(false)
  const immediate = deviceStatus(needsInstall)

  useEffect(() => {
    if (immediate) return
    let cancelled = false
    readStatus()
      .then((status) => {
        if (!cancelled) setRead(status)
      })
      .catch(() => {
        // the key couldn't be read: "Ativar" tries again
        if (!cancelled) setRead("inactive")
      })
    return () => {
      cancelled = true
    }
  }, [attempt, immediate])

  useEffect(() => {
    function onVisibilityChange() {
      if (document.visibilityState === "visible") setAttempt((n) => n + 1)
    }
    document.addEventListener("visibilitychange", onVisibilityChange)
    return () => document.removeEventListener("visibilitychange", onVisibilityChange)
  }, [])

  // after enable / disable: read the device again (the new status replaces the old one when it arrives)
  const reread = useCallback(() => setAttempt((n) => n + 1), [])

  const enable = useCallback(async () => {
    if (!pushSupported()) return
    setPending(true)
    try {
      const permission = await Notification.requestPermission()
      if (permission !== "granted") return // denied shows from the permission itself; dismissed stays inactive
      const key = await loadVapidKey()
      if (!key) return
      await subscribeDevice(await navigator.serviceWorker.ready, key)
      setRead("active")
      toast.success("Notificações ativadas neste aparelho")
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível ativar as notificações"))
    } finally {
      setPending(false)
      reread()
    }
  }, [reread])

  const disable = useCallback(async () => {
    setPending(true)
    try {
      const subscription = await currentPushSubscription()
      if (subscription) {
        await unsubscribePush(subscription.endpoint)
        await subscription.unsubscribe()
      }
      setRead("inactive")
      toast.success("Notificações desativadas neste aparelho")
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível desativar as notificações"))
    } finally {
      setPending(false)
      reread()
    }
  }, [reread])

  return { status: immediate ?? read, pending, enable, disable }
}
