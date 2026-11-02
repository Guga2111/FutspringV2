import type { Page } from "@playwright/test"
import * as f from "./fixtures"

type Handler = (match: RegExpMatchArray) => unknown

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
  [/^\/peladas\/\d+\/invitations$/, () => []],
  [/^\/peladas\/\d+\/invite-link$/, () => null],
  [/^\/peladas\/\d+\/finance$/, () => f.financeOverview],
  [/^\/peladas\/\d+\/finance\/lists\/\d+$/, () => f.financeDetail],
  [/^\/peladas\/\d+\/finance\/movements$/, () => ({ items: [], hasMore: false })],
  [/^\/dailies\/(\d+)$/, (m) => ({ [f.SCHEDULED_ID]: f.scheduledDaily, [f.LIVE_ID]: f.liveDaily, [f.FINISHED_ID]: f.finishedDaily })[Number(m[1])]],
  [/^\/users\/\d+$/, () => f.profile],
  [/^\/users\/\d+\/peladas$/, () => f.myPeladas],
  [/^\/users\/\d+\/stats$/, () => f.stats],
  [/^\/users\/\d+\/stats\/timeline$/, () => f.timeline],
  [/^\/users\/\d+\/stats\/matches$/, () => f.matchHistory],
  [/^\/invitations$/, () => f.invitations],
  [/^\/notifications$/, () => f.notifications],
  [/^\/notifications\/unread-count$/, () => ({ count: 1 })],
  [/^\/notifications\/preferences$/, () => f.notificationPreferences],
]

export async function mockApi(page: Page): Promise<string[]> {
  const unmatched: string[] = []
  await page.route(/\/api\/v1\//, async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^.*\/api\/v1/, "")
    if (request.method() === "GET") {
      for (const [pattern, handler] of routes) {
        const match = path.match(pattern)
        if (!match) continue
        const body = handler(match)
        if (body === undefined) break
        return body === null ? route.fulfill({ status: 204 }) : route.fulfill({ status: 200, json: body })
      }
    }
    unmatched.push(`${request.method()} ${path}`)
    return route.fulfill({ status: 404, json: { status: 404, message: "Não encontrado", timestamp: f.NOW.toISOString() } })
  })
  // no realtime server: the client stays in its reconnecting state, which shows nothing on these screens
  await page.route(/\/ws\//, (route) => route.abort())
  return unmatched
}

export async function login(page: Page) {
  await page.addInitScript((user) => {
    localStorage.setItem("futspring_token", "visual-test-token")
    localStorage.setItem("futspring_user", JSON.stringify(user))
  }, f.me)
}
