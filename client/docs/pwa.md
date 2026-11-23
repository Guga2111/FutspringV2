# PWA

> Manifest, service worker, updates, offline behavior, Web Push and installation. Part of the [frontend guide](README.md).

## Rules

1. The service worker never caches an API response; offline, only the shell opens.
2. A new version waits for the user ("Atualizar" toast); never reload on its own.
3. A push always shows a notification, even with the app open.
4. `sw.js`, `manifest.webmanifest` and `index.html` are served with `Cache-Control: no-cache`.

## PWA

The app installs on the home screen. Server-side Web Push delivery is documented in [Web Push](../../core/docs/notifications.md#web-push).

- **Build:** `vite-plugin-pwa` in `vite.config.ts` with `strategies: "injectManifest"`, `registerType: "prompt"`, `injectRegister: false`. It generates `manifest.webmanifest` (`id`/`scope` `/`, `start_url` `/home` (logged out, `PrivateRoute` sends it to `/auth`), `display: standalone`, `lang: pt-BR`, background/theme `#F7F7F9` (the light page; the `theme-color` meta still follows the theme live), icons 192 / 512 / maskable 512) and injects its `<link rel="manifest">`. `index.html` has the iOS tags (`apple-touch-icon` 180 PNG, `apple-mobile-web-app-title` / `-capable` / `-status-bar-style`).
- **Service worker (`src/sw.ts`):** `precacheAndRoute(self.__WB_MANIFEST)` + `cleanupOutdatedCaches()`, and a `NavigationRoute` to `index.html` whose `denylist` leaves `/api`, `/ws` and `/files` to the network. **No API response is cached** (stale attendance, spots or finance cause real mistakes): offline, only the shell opens and the screens show their error states. The precache takes the build (`js, css, html, woff2, webp, png, svg`) except `landing/**` (large photos), the 512 icons (read only at install) and the Inter subsets other than Latin (fetched by `unicode-range` only for characters pt-BR doesn't use); `.jpg` isn't in the pattern. A `SKIP_WAITING` message activates a waiting version.
- **Updates:** `components/layout/PwaUpdater` (mounted in `main.tsx`, so public pages too) registers it with `useRegisterSW` (`virtual:pwa-register/react`) and, when a new build is waiting, shows a toast that stays ("Nova versão disponível · Atualizar" → `updateServiceWorker(true)`); never on its own, which would reload in the middle of a form. `bun run dev` has no service worker (the plugin's default); try it with `bun run build && bun run preview`.
- **Offline:** `useOnline()` drives the shell's "Sem conexão" `Alert` (`AppLayout`).
- **Web Push** (`api/push.ts`, `usePushSubscription`, `lib/push.ts`): standard VAPID push, each browser through its own push service. `src/sw.ts` handles `push`: reads the backend's JSON (`PushPayload {title, body, url, tag, unreadCount}`) and **always** shows a notification (`icon` `/pwa-192.png`, `badge` `/pwa-badge-96.png`, `tag`: a newer one of the same session replaces it, `data.url`), even with the app open (Safari revokes a subscription that receives a push without showing one), and sets the icon badge (`setAppBadge` / `clearAppBadge` with 0). `notificationclick` closes it and, with an app window open, `focus()` + `navigate(url)`, else `clients.openWindow(url)`. `pushsubscriptionchange` isn't handled: `syncPushSubscription` sends the subscription again when the app opens. The worker has no token, so it marks nothing read: the url carries `?notificacao={id}` and `useNotifications` does it. The app also syncs the badge with the bell's counter and clears it on logout. Entry points: the Home `EnablePushCard` (installed app, status `inactive`: "Ative as notificações" + "Ativar notificações"; ✕ stores the date in `localStorage.push_card_dismissed`, back after 30 days) and the "Neste aparelho" block of `/settings#notificacoes`. A toast (STOMP) and a system notification both show with the app open: accepted in V1.
- **Install:** `captureInstallPrompt()` (`lib/installPrompt.ts`, called in `main.tsx` before the render, because Chrome can fire `beforeinstallprompt` before any page mounts) keeps the event; `usePwaInstall()` turns user agent, touch points, `display-mode: standalone` / `navigator.standalone` and the event into an `InstallContext` (`utils/pwaPlatform.ts`: `installed`, `ios-safari`, `ios-other`, `in-app`, `android-prompt` (Android only; desktop Chrome gets the QR Code), `android-manual`, `desktop`). Entry points: `/instalar`, `InstallAppCard` on Home and the `/auth` QR Code.
- **Icons:** generated from `public/gerrard.webp` with `sips` (the largest logo there is, so the 512 ones are upscaled; regenerate from a vector logo when there is one): crop the 2 px border, resize, pad with its background `#9E9C96`:
  ```bash
  sips -s format png --cropToHeightWidth 158 186 public/gerrard.webp --out /tmp/src.png
  # name, logo width, square size: pwa-192.png 184 192 · pwa-512.png 490 512 · pwa-maskable-512.png 330 512 (inside the 80% safe area) · apple-touch-icon.png 172 180
  sips --resampleWidth 490 /tmp/src.png --out /tmp/t.png && sips --padToHeightWidth 512 512 --padColor 9E9C96 /tmp/t.png --out public/pwa-512.png
  ```
- **Server cache:** `sw.js`, `manifest.webmanifest` and `index.html` must be served with `Cache-Control: no-cache` (hashed assets keep the long cache), or a phone can stay on an old version for days: nginx config in [CI/CD](../../core/docs/deploy.md#cicd).
