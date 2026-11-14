import { expect, test, type WebSocketRoute } from "@playwright/test"
import { login, mockApi } from "./mockApi"
import { me, NOW } from "./fixtures"

function token(expiresAt: number) {
  return `h.${Buffer.from(JSON.stringify({ sub: "realtime-test", exp: expiresAt })).toString("base64url")}.s`
}

for (const refreshFails of [false, true]) {
  test(`expired realtime connection ${refreshFails ? "stops retrying when renewal fails" : "renews and restores subscriptions"}`, async ({ page }) => {
    await page.clock.setFixedTime(NOW)
    const unmatched = await mockApi(page)
    await login(page)
    const original = token(NOW.getTime() / 1000 + 60)
    const renewed = token(NOW.getTime() / 1000 + 3600)
    let refreshes = 0
    await page.route(/\/auth\/refresh$/, (route) => {
      refreshes++
      return refreshes > 1 && refreshFails
        ? route.fulfill({ status: 503, json: { message: "Indisponível" } })
        : route.fulfill({ json: { token: refreshes === 1 ? original : renewed, user: me } })
    })
    await page.route(/\/ws\//, (route) => new URL(route.request().url()).pathname.endsWith("/info")
      ? route.fulfill({ json: { websocket: true, cookie_needed: false, origins: ["*:*"], entropy: 1 } })
      : route.abort())
    const connections: WebSocketRoute[] = []
    const credentials: string[] = []
    const subscriptions: string[][] = []
    await page.routeWebSocket(/\/ws\/.*\/websocket$/, (socket) => {
      const index = connections.length
      connections.push(socket)
      subscriptions.push([])
      socket.onMessage((raw) => {
        for (const frame of JSON.parse(raw.toString()) as string[]) {
          if (frame.startsWith("CONNECT\n")) {
            const authorization = frame.split("\n").find((line) => line.startsWith("Authorization:")) ?? ""
            credentials.push(authorization)
            if (index > 0 && authorization.endsWith(original)) {
              socket.send(`a${JSON.stringify(["ERROR\nmessage:Sessão expirada\n\n\0"])}`)
              socket.close({ code: 1008, reason: "expired" })
            } else {
              socket.send(`a${JSON.stringify(["CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0"])}`)
            }
          } else if (frame.startsWith("SUBSCRIBE\n")) {
            subscriptions[index].push(frame.split("\n").find((line) => line.startsWith("destination:")) ?? "")
          }
        }
      })
      socket.send("o")
    })
    await page.goto("/pelada/1")
    await page.getByRole("button", { name: "Abrir chat", exact: true }).click()
    const destinations = ["destination:/topic/pelada/1", "destination:/user/queue/notifications"]
    await expect.poll(() => subscriptions[0]).toEqual(expect.arrayContaining(destinations))
    expect(credentials).toEqual([`Authorization:Bearer ${original}`])

    await page.clock.setFixedTime(new Date(NOW.getTime() + 70_000))
    connections[0].close({ code: 1008, reason: "expired" })

    if (refreshFails) {
      await expect.poll(() => credentials.length, { timeout: 15_000 }).toBe(4)
      await page.waitForTimeout(4500)
      expect(credentials).toHaveLength(4)
      expect(credentials.slice(1)).toEqual(Array(3).fill(`Authorization:Bearer ${original}`))
      expect(subscriptions.slice(1).flat()).toEqual([])
    } else {
      await expect.poll(() => subscriptions[1]).toEqual(expect.arrayContaining(destinations))
      expect(credentials).toEqual([`Authorization:Bearer ${original}`, `Authorization:Bearer ${renewed}`])
      expect(refreshes).toBe(2)
    }
    expect(unmatched).toEqual([])
  })
}
