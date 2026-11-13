import { expect, test } from "@playwright/test"
import { login, mockApi } from "./mockApi"
import { dailies, members, NOW, peladaDetail } from "./fixtures"

const PHOTO = new URL("./assets/qr.png", import.meta.url).pathname

test("private history photos load near the viewport and stay unloaded when hidden on mobile", async ({ page }, info) => {
  await page.clock.setFixedTime(NOW)
  const unmatched = await mockApi(page)
  await login(page)
  const history = Array.from({ length: 40 }, (_, i) => ({
    ...dailies[1], id: 600 + i,
    dailyDate: new Date(Date.UTC(2026, 9, 29 - i * 7)).toISOString().slice(0, 10),
    championImage: `champion-${i}.png`,
  }))
  await page.route(/\/peladas\/1\/dailies$/, (route) => route.fulfill({ json: [dailies[0], ...history] }))
  const requested: string[] = []
  await page.route(/\/api\/v1\/files\//, async (route) => {
    expect(route.request().headers().authorization).toMatch(/^Bearer /)
    requested.push(new URL(route.request().url()).pathname.split("/").at(-1) ?? "")
    await route.fulfill({ contentType: "image/png", path: PHOTO })
  })
  await page.goto("/pelada/1/sessoes")
  await expect(page.getByRole("heading", { name: /Histórico/ })).toBeVisible()
  await page.waitForLoadState("networkidle")
  expect(requested).not.toContain("champion-39.png")
  if (info.project.name.startsWith("mobile")) {
    expect(requested).toHaveLength(0)
    // Gallery photos are visible on mobile, while the list's thumbnail column is hidden.
    await page.getByRole("radio", { name: "Ver em galeria" }).click()
  }
  await expect.poll(() => requested.length).toBeGreaterThan(0)
  expect(requested.length).toBeLessThan(history.length)
  await page.locator('a[href="/daily/639"]').scrollIntoViewIfNeeded()
  await expect.poll(() => requested.includes("champion-39.png")).toBe(true)
  expect(unmatched).toEqual([])
})

test("repeated private avatars share one authenticated photo request", async ({ page }) => {
  await page.clock.setFixedTime(NOW)
  const unmatched = await mockApi(page)
  await login(page)
  await page.route(/\/peladas\/1$/, (route) => route.fulfill({ json: {
    ...peladaDetail, members: members.map((member) => ({ ...member, image: "shared.png" })),
  } }))
  let requests = 0
  await page.route(/\/api\/v1\/files\/shared.png$/, async (route) => {
    requests++
    expect(route.request().headers().authorization).toMatch(/^Bearer /)
    await route.fulfill({ contentType: "image/png", path: PHOTO })
  })
  await page.goto("/pelada/1/membros")
  await expect(page.locator('img[src^="blob:"]').first()).toBeVisible()
  await page.getByText(members.at(-1)?.username ?? "", { exact: true }).last().scrollIntoViewIfNeeded()
  await page.waitForLoadState("networkidle")
  expect(requests).toBe(1)
  expect(unmatched).toEqual([])
})
