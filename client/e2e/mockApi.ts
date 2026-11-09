import type { Page } from "@playwright/test"
import * as f from "./fixtures"

const QR_IMAGE = new URL("./assets/qr.png", import.meta.url).pathname

type Handler = (match: RegExpMatchArray, url: URL) => unknown

// the history filters (from / to / operations), so a filter picked in a test shows what the API would answer;
// without a filter the fixture page as it is (its total may count more pages)
function filterHistory<T>(page: { items: T[]; hasMore: boolean; total: number }, url: URL, day: (item: T) => string, operation?: (item: T) => string) {
  const from = url.searchParams.get("from")
  const to = url.searchParams.get("to")
  const operations = url.searchParams.getAll("operations")
  if (!from && !to && operations.length === 0) return page
  const items = page.items.filter((item) =>
    (!from || day(item) >= from) && (!to || day(item) <= to) && (operations.length === 0 || !operation || operations.includes(operation(item))))
  return { items, hasMore: false, total: items.length }
}

// GET routes the screens read. Anything else answers 404 and is recorded, so a new request
// shows up as a test failure instead of an error state baked into a screenshot.
const routes: [RegExp, Handler][] = [
  [/^\/peladas\/my$/, () => f.myPeladas],
  [/^\/peladas\/(\d+)$/, () => f.peladaDetail],
  [/^\/peladas\/\d+\/dailies$/, () => f.dailies],
  [/^\/peladas\/\d+\/ranking$/, () => f.ranking],
  [/^\/peladas\/\d+\/awards$/, () => f.awards],
  [/^\/peladas\/\d+\/attendance$/, () => f.attendance],
  [/^\/peladas\/\d+\/messages$/, () => []],
  [/^\/peladas\/\d+\/invitations$/, (_, url) =>
    url.searchParams.get("pending") === "false" ? f.peladaInvitationsHistory : f.peladaInvitationsPending],
  [/^\/peladas\/\d+\/invite-link$/, () => f.inviteLink],
  [/^\/peladas\/\d+\/finance$/, () => f.financeOverview],
  [/^\/peladas\/\d+\/finance\/lists\/\d+$/, () => f.financeDetail],
  [/^\/peladas\/\d+\/finance\/movements$/, (_, url) => filterHistory(f.financeMovements, url, (m) => m.effectiveDate)],
  [/^\/peladas\/\d+\/finance\/audit$/, (_, url) => filterHistory(f.financeAudit, url, (a) => a.createdAt.slice(0, 10), (a) => a.operation)],
  // both finance tours already seen, so the screenshots show the page, not the tour
  [/^\/tours$/, () => ({ seen: ["FINANCE_MANAGER", "FINANCE_MEMBER"] })],
  [/^\/dailies\/(\d+)$/, (m) => ({ [f.SCHEDULED_ID]: f.scheduledDaily, [f.CONFIRMED_ID]: f.confirmedDaily, [f.LIVE_ID]: f.liveDaily, [f.FINISHED_ID]: f.finishedDaily })[Number(m[1])]],
  [/^\/users\/\d+$/, () => f.profile],
  [/^\/users\/\d+\/peladas$/, () => f.myPeladas],
  [/^\/users\/\d+\/stats$/, () => f.stats],
  [/^\/users\/\d+\/stats\/timeline$/, () => f.timeline],
  [/^\/users\/\d+\/stats\/matches$/, () => f.matchHistory],
  [/^\/invitations$/, () => f.invitations],
  [/^\/invitations\/(\d+)$/, (m) => f.invitations.find((i) => i.id === Number(m[1]))],
  [/^\/invite-links\/[^/]+$/, () => f.invitations[0].pelada],
  [/^\/notifications$/, () => f.notifications],
  [/^\/notifications\/unread-count$/, () => ({ count: 1 })],
  [/^\/notifications\/preferences$/, () => f.notificationPreferences],
  [/^\/users\/me\/deletion-blockers$/, () => f.deletionBlockers],
  [/^\/push\/vapid-public-key$/, () => f.vapidPublicKey],
  [/^\/platform\/overview$/, () => f.platformOverview],
  [/^\/platform\/peladas$/, () => f.platformPeladas],
  [/^\/platform\/peladas\/\d+$/, () => f.platformPelada],
  [/^\/platform\/payments$/, () => f.platformPayments],
  [/^\/platform\/users$/, () => f.platformUsers],
]

// What a test can set in window.__pushState (an init script added after mockApi's) for the device's Web Push
export interface PushState {
  permission?: NotificationPermission
  subscribed?: boolean
}

export async function mockApi(page: Page): Promise<string[]> {
  const unmatched: string[] = []
  await page.route(/\/api\/v1\//, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^.*\/api\/v1/, "")
    if (request.method() === "GET") {
      for (const [pattern, handler] of routes) {
        const match = path.match(pattern)
        if (!match) continue
        const body = handler(match, url)
        if (body === undefined) break
        return body === null ? route.fulfill({ status: 204 }) : route.fulfill({ status: 200, json: body })
      }
    }
    unmatched.push(`${request.method()} ${path}`)
    return route.fulfill({ status: 404, json: { status: 404, message: "Não encontrado", timestamp: f.NOW.toISOString() } })
  })
  // protected images: the generated QR placeholder
  await page.route(/\/finance\/qr\/\d+$/, (route) => route.fulfill({ status: 200, contentType: "image/png", path: QR_IMAGE }))
  // no realtime server: the client stays in its reconnecting state, which shows nothing on these screens
  await page.route(/\/ws\//, (route) => route.abort())
  await mockPushDevice(page)
  return unmatched
}

export async function login(page: Page) {
  await page.addInitScript((user) => {
    localStorage.setItem("futspring_token", "visual-test-token")
    localStorage.setItem("futspring_user", JSON.stringify(user))
  }, f.me)
}

// The browser's Web Push, deterministic: notifications not asked yet and no subscription unless a test sets
// window.__pushState. The real service worker is blocked (playwright.config.ts), so its registration is a fake
// whose pushManager answers from that state. Read when called, so a later init script can still change it.
async function mockPushDevice(page: Page) {
  await page.addInitScript(() => {
    const state = (): PushState => ({ permission: "default", subscribed: false, ...(window as { __pushState?: PushState }).__pushState })
    const subscription = {
      endpoint: "https://fcm.googleapis.com/fcm/send/visual-test",
      options: { applicationServerKey: null },
      toJSON: () => ({ endpoint: subscription.endpoint, expirationTime: null, keys: { p256dh: "BPk", auth: "QUJD" } }),
      unsubscribe: async () => true,
    }
    const registration = {
      pushManager: {
        getSubscription: async () => (state().subscribed ? subscription : null),
        subscribe: async () => subscription,
      },
    }
    if ("Notification" in window) {
      Object.defineProperty(Notification, "permission", { get: () => state().permission, configurable: true })
      Notification.requestPermission = async () => state().permission ?? "default"
    }
    if ("serviceWorker" in navigator) {
      Object.defineProperty(navigator.serviceWorker, "getRegistration", { value: async () => registration, configurable: true })
      Object.defineProperty(navigator.serviceWorker, "ready", { get: () => Promise.resolve(registration), configurable: true })
    }
  })
}
