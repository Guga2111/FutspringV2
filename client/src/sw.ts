/// <reference lib="webworker" />
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute, type PrecacheEntry } from "workbox-precaching"
import { NavigationRoute, registerRoute } from "workbox-routing"

// Service worker of the installable app (built by vite-plugin-pwa, strategy injectManifest).
// Only the app shell works offline: the build is precached and every navigation answers index.html.
// No API request is cached: stale attendance, spots or finance would cause real mistakes.
declare const self: ServiceWorkerGlobalScope & { __WB_MANIFEST: (PrecacheEntry | string)[] }

cleanupOutdatedCaches()
precacheAndRoute(self.__WB_MANIFEST)

// The SPA's routes open from the precache; the API, the realtime socket and the uploaded files go to the network
registerRoute(new NavigationRoute(createHandlerBoundToURL("index.html"), { denylist: [/^\/api/, /^\/ws/, /^\/files/] }))

// "Atualizar" in PwaUpdater (updateServiceWorker) activates the waiting version, then the page reloads
self.addEventListener("message", (event) => {
  if (event.data?.type === "SKIP_WAITING") void self.skipWaiting()
})

// Web Push. The payload is WebPushDispatcher's JSON (types/push.ts PushPayload; the worker has its own tsconfig,
// so the shape is repeated here). The notification is always shown, even with the app open: Safari revokes the
// subscription of an app that receives a push without showing one, and Chrome shows a generic notice instead.
interface PushPayload {
  title: string
  body: string
  url: string
  tag: string
  unreadCount: number
}

function readPayload(event: PushEvent): PushPayload | null {
  try {
    return (event.data?.json() as PushPayload | undefined) ?? null
  } catch {
    return null
  }
}

self.addEventListener("push", (event) => {
  const payload = readPayload(event)
  const title = payload?.title ?? "Futspring"
  const shown = self.registration.showNotification(title, {
    body: payload?.body ?? "Você tem uma nova notificação.",
    icon: "/pwa-192.png",
    // Android's status bar icon: a white silhouette on transparency
    badge: "/pwa-badge-96.png",
    // a newer notification of the same session (else pelada) replaces the one on screen
    tag: payload?.tag,
    data: { url: payload?.url ?? "/home" },
  })
  const count = payload?.unreadCount ?? 0
  const badge = count > 0 ? self.navigator.setAppBadge?.(count) : self.navigator.clearAppBadge?.()
  event.waitUntil(Promise.all([shown, badge?.catch(() => undefined)]))
})

// Opens the notification's link in the app: an open window is focused and navigated (the reload reads
// ?notificacao= and marks it read), else a new one opens
self.addEventListener("notificationclick", (event) => {
  event.notification.close()
  const path = (event.notification.data as { url?: string } | null)?.url ?? "/home"
  const url = new URL(path, self.location.origin).href
  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true })
      const open = windows.find((client) => new URL(client.url).origin === self.location.origin)
      if (open) {
        try {
          const focused = await open.focus()
          await focused.navigate(url)
          return
        } catch {
          // an uncontrolled window can't be navigated from here: open a new one
        }
      }
      await self.clients.openWindow(url)
    })(),
  )
})
