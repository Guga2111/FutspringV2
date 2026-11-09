import { pushSubscriptionSchema, type PushSubscriptionRequest } from "@/schemas/push"

// Browser side of Web Push (no React): what this device supports, its current subscription and the app badge.
// The server side is api/push.ts; usePushSubscription and AuthContext's logout put both together.

export function pushSupported(): boolean {
  return typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window
}

// The applicationServerKey arrives in base64url; pushManager.subscribe wants the bytes
export function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=")
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

// Whether a subscription was made with this VAPID key (a new server key needs a new subscription)
export function sameServerKey(current: ArrayBuffer | null | undefined, publicKey: string): boolean {
  if (!current) return true // not exposed by the browser: assume it is the same
  const a = new Uint8Array(current)
  const b = base64UrlToBytes(publicKey)
  return a.length === b.length && a.every((byte, i) => byte === b[i])
}

// The request body from the browser's PushSubscription.toJSON(); null when the browser left a key out
export function subscriptionRequest(json: PushSubscriptionJSON): PushSubscriptionRequest | null {
  const parsed = pushSubscriptionSchema.safeParse(json)
  return parsed.success ? parsed.data : null
}

// The registration made by PwaUpdater; undefined in dev (no service worker) or before it registered
export async function pushRegistration(): Promise<ServiceWorkerRegistration | undefined> {
  if (!pushSupported()) return undefined
  return navigator.serviceWorker.getRegistration()
}

export async function currentPushSubscription(): Promise<PushSubscription | null> {
  const registration = await pushRegistration()
  return (await registration?.pushManager.getSubscription()) ?? null
}

type BadgeNavigator = Navigator & { setAppBadge?: (count?: number) => Promise<void>; clearAppBadge?: () => Promise<void> }

// The count on the installed app's icon (iOS 16.4+ home screen app, Chrome on Android/desktop); a no-op elsewhere
export function setAppBadge(count: number) {
  const nav = navigator as BadgeNavigator
  const done = count > 0 ? nav.setAppBadge?.(count) : nav.clearAppBadge?.()
  done?.catch(() => {
    // not allowed here (permission, a browser tab): nothing to show
  })
}
