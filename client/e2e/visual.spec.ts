import { expect, test, type Page } from "@playwright/test"
import { mockApi, login } from "./mockApi"
import { FINISHED_ID, LIVE_ID, NOW, PELADA_ID, SCHEDULED_ID } from "./fixtures"

const PELADA = `/pelada/${PELADA_ID}`

const pages: { name: string; path: string; auth?: boolean }[] = [
  { name: "auth", path: "/auth", auth: false },
  { name: "home", path: "/home" },
  { name: "pelada-overview", path: PELADA },
  { name: "pelada-members", path: `${PELADA}/membros` },
  { name: "pelada-sessions", path: `${PELADA}/sessoes` },
  { name: "pelada-ranking", path: `${PELADA}/ranking` },
  { name: "pelada-awards", path: `${PELADA}/premios` },
  { name: "pelada-finance", path: `${PELADA}/financeiro` },
  { name: "pelada-settings", path: `${PELADA}/settings` },
  { name: "daily-scheduled", path: `/daily/${SCHEDULED_ID}` },
  { name: "daily-live", path: `/daily/${LIVE_ID}` },
  { name: "daily-finished", path: `/daily/${FINISHED_ID}` },
  { name: "profile", path: "/profile/1" },
  { name: "notification-settings", path: "/settings/notifications" },
]

async function open(page: Page, path: string, auth = true): Promise<string[]> {
  await page.clock.setFixedTime(NOW)
  const unmatched = await mockApi(page)
  if (auth) await login(page)
  await page.goto(path)
  await page.waitForLoadState("networkidle")
  await page.evaluate(() => document.fonts.ready)
  return unmatched
}

async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow, "horizontal scroll").toBeLessThanOrEqual(0)
}

test("document language is pt-BR", async ({ page }) => {
  await open(page, "/auth", false)
  await expect(page.locator("html")).toHaveAttribute("lang", "pt-BR")
})

for (const { name, path, auth } of pages) {
  test(`page ${name}`, async ({ page }) => {
    const unmatched = await open(page, path, auth)
    await expectNoHorizontalScroll(page)
    await expect(page).toHaveScreenshot(`${name}.png`, { fullPage: true })
    expect(unmatched, "requests without a fixture").toEqual([])
  })
}

test("dialog compare players", async ({ page }) => {
  await open(page, `${PELADA}/ranking`)
  await page.getByRole("button", { name: "Ações", exact: true }).click()
  await page.getByRole("option", { name: /Comparar Jogadores/ }).click()
  await expect(page.getByRole("dialog")).toHaveScreenshot("dialog-compare-players.png")
})

test("dialog attendance ranking", async ({ page }) => {
  await open(page, `${PELADA}/ranking`)
  await page.getByRole("button", { name: "Ações", exact: true }).click()
  await page.getByRole("option", { name: /Ranking de Presença/ }).click()
  await page.waitForLoadState("networkidle")
  await expect(page.getByRole("dialog")).toHaveScreenshot("dialog-attendance-ranking.png")
})

test("dialog results", async ({ page }) => {
  await open(page, `/daily/${LIVE_ID}`)
  await page.getByRole("button", { name: "Lançar resultados" }).first().click()
  await expect(page.getByRole("dialog")).toHaveScreenshot("dialog-results.png")
})
