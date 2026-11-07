// mirrors VapidPublicKeyDTO: the applicationServerKey of pushManager.subscribe (base64url)
export interface VapidPublicKey {
  publicKey: string
}

// Web Push on this device (usePushSubscription):
// unsupported: the browser has no Web Push · needsInstall: an iPhone outside the home screen app (iOS only pushes there)
// unconfigured: the server has no VAPID key · denied: the user blocked notifications for the app
// inactive: can be turned on · active: this device is subscribed for the logged-in account
export type PushStatus = "unsupported" | "needsInstall" | "unconfigured" | "denied" | "inactive" | "active"

// The JSON the backend's WebPushDispatcher encrypts for the service worker (src/sw.ts reads the same shape)
export interface PushPayload {
  title: string
  body: string
  // app path with ?notificacao={id}: the app marks it read when it opens
  url: string
  // one per session (else pelada): a newer one with the same tag replaces the one shown
  tag: string
  unreadCount: number
}
