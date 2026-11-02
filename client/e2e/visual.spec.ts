import { expect, test, type Page } from "@playwright/test"
import { mockApi, login } from "./mockApi"
import { FINISHED_ID, LIVE_ID, NOW, PELADA_ID, SCHEDULED_ID, financeMemberDetail, financeMemberOverview } from "./fixtures"

const PELADA = `/pelada/${PELADA_ID}`

// ready: what shows that the lazy page chunk rendered (default: a heading)
const pages: { name: string; path: string; auth?: boolean; ready?: string }[] = [
  { name: "auth", path: "/auth", auth: false, ready: "form" },
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

async function open(page: Page, path: string, auth = true, ready = "h1, h2"): Promise<string[]> {
  await page.clock.setFixedTime(NOW)
  const unmatched = await mockApi(page)
  if (auth) await login(page)
  await page.goto(path)
  await expect(page.locator(ready).first()).toBeVisible()
  await settle(page)
  return unmatched
}

// data loaded (no request in flight, no Skeleton left), fonts ready and no hover state
async function settle(page: Page) {
  await page.mouse.move(0, 0)
  await page.waitForLoadState("networkidle")
  await expect(page.locator(".animate-pulse")).toHaveCount(0)
  await page.evaluate(() => document.fonts.ready)
}

// Centered dialogs sit on fractional pixels, so their text antialiasing varies a little between
// runs; a token change still alters nearly every pixel
// (the config's absolute maxDiffPixels is lifted here, the ratio is the limit)
const DIALOG_SHOT = { maxDiffPixelRatio: 0.04, maxDiffPixels: Number.MAX_SAFE_INTEGER }

async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow, "horizontal scroll").toBeLessThanOrEqual(0)
}

test("document language is pt-BR", async ({ page }) => {
  await open(page, "/auth", false, "form")
  await expect(page.locator("html")).toHaveAttribute("lang", "pt-BR")
})

for (const { name, path, auth, ready } of pages) {
  test(`page ${name}`, async ({ page }) => {
    const unmatched = await open(page, path, auth, ready)
    await expectNoHorizontalScroll(page)
    await expect(page).toHaveScreenshot(`${name}.png`, { fullPage: true })
    expect(unmatched, "requests without a fixture").toEqual([])
  })
}

test("dialog compare players", async ({ page }) => {
  await open(page, `${PELADA}/ranking`)
  await page.getByRole("button", { name: "Ações", exact: true }).click()
  await page.getByRole("option", { name: /Comparar Jogadores/ }).click()
  await settle(page)
  await expect(page.getByRole("dialog")).toHaveScreenshot("dialog-compare-players.png", DIALOG_SHOT)
})

test("dialog attendance ranking", async ({ page }) => {
  await open(page, `${PELADA}/ranking`)
  await page.getByRole("button", { name: "Ações", exact: true }).click()
  await page.getByRole("option", { name: /Ranking de Presença/ }).click()
  await settle(page)
  await expect(page.getByRole("dialog")).toHaveScreenshot("dialog-attendance-ranking.png", DIALOG_SHOT)
})

test("dialog results", async ({ page }) => {
  await open(page, `/daily/${LIVE_ID}`)
  await page.getByRole("button", { name: "Lançar resultados" }).first().click()
  await settle(page)
  await expect(page.getByRole("dialog")).toHaveScreenshot("dialog-results.png", DIALOG_SHOT)
})

const FINANCE = `${PELADA}/financeiro`

test("finance manager: collection with its Pix", async ({ page }) => {
  const unmatched = await open(page, FINANCE)
  await page.getByRole("button", { name: /Mensalidade de novembro/ }).click()
  await page.getByRole("button", { name: "Informações da cobrança e Pix" }).click()
  await settle(page)
  await expect(page).toHaveScreenshot("finance-detail-manager.png", { fullPage: true })
  expect(unmatched, "requests without a fixture").toEqual([])
})

test("finance manager: cash with numbered pages", async ({ page }) => {
  const unmatched = await open(page, FINANCE)
  await page.getByRole("tab", { name: "Caixa" }).click()
  await settle(page)
  await expect(page).toHaveScreenshot("finance-cash.png", { fullPage: true })
  expect(unmatched, "requests without a fixture").toEqual([])
})

test("finance manager: settings", async ({ page }) => {
  const unmatched = await open(page, FINANCE)
  await page.getByRole("tab", { name: "Configurações" }).click()
  await settle(page)
  await expect(page).toHaveScreenshot("finance-settings.png", { fullPage: true })
  expect(unmatched, "requests without a fixture").toEqual([])
})

test("finance member: my charges, Pix and report", async ({ page }) => {
  await page.clock.setFixedTime(NOW)
  const unmatched = await mockApi(page)
  // registered later, so they answer before the manager fixtures
  await page.route(/\/finance(\?|$)/, (route) => route.fulfill({ json: financeMemberOverview }))
  await page.route(/\/finance\/lists\/\d+$/, (route) => route.fulfill({ json: financeMemberDetail }))
  await login(page)
  await page.goto(FINANCE)
  await expect(page.locator("h1, h2").first()).toBeVisible()
  await settle(page)
  await expect(page).toHaveScreenshot("finance-member.png", { fullPage: true })
  await page.getByRole("button", { name: /Mensalidade de novembro/ }).click()
  await settle(page)
  await expect(page).toHaveScreenshot("finance-member-detail.png", { fullPage: true })
  await page.getByRole("button", { name: "Informar pagamento" }).click()
  await settle(page)
  await expect(page.getByRole("dialog")).toHaveScreenshot("finance-member-report.png", DIALOG_SHOT)
  expect(unmatched, "requests without a fixture").toEqual([])
})
