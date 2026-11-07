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

// Web Push (push / notificationclick handlers) comes in the next part of the PWA spec
