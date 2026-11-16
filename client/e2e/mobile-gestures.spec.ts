import { test, expect, type Page } from "@playwright/test"
import { login, mockApi } from "./mockApi"
import { myPeladas, PELADA_ID } from "./fixtures"

// The mobile shell (below md): bottom tabs, the "+" menu, swipe back from the edge and pull to refresh.
// Touches go through CDP (Input.dispatchTouchEvent), as a finger would.

const IOS = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1"
const ANDROID = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

test.beforeEach(async ({ page }, testInfo) => {
  test.skip(!testInfo.project.name.startsWith("mobile"), "the mobile shell lives below md")
  await mockApi(page)
  await login(page)
})

async function drag(page: Page, from: { x: number; y: number }, to: { x: number; y: number }, steps = 10) {
  const cdp = await page.context().newCDPSession(page)
  await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [from] })
  for (let i = 1; i <= steps; i++) {
    const point = { x: from.x + ((to.x - from.x) * i) / steps, y: from.y + ((to.y - from.y) * i) / steps }
    await cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [point] })
  }
  await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] })
}

function counter(page: Page, path: RegExp) {
  let count = 0
  page.on("request", (request) => {
    if (request.method() === "GET" && path.test(new URL(request.url()).pathname)) count++
  })
  return () => count
}

test.describe("navigation", () => {
  test("bottom tabs replace the hamburger and mark the current tab", async ({ page }) => {
    await page.goto("/home")
    const nav = page.getByRole("navigation", { name: "Navegação principal" })
    await expect(nav).toBeVisible()
    await expect(page.getByRole("button", { name: "Abrir menu" })).toHaveCount(0)
    await expect(nav.getByRole("link", { name: "Início" })).toHaveAttribute("aria-current", "page")
    await nav.getByRole("link", { name: "Peladas" }).click()
    await expect(page).toHaveURL(/\/peladas$/)
    await expect(nav.getByRole("link", { name: "Peladas" })).toHaveAttribute("aria-current", "page")
    // the pelada belongs to the Peladas tab
    await page.getByRole("link", { name: /Pelada do Fut/ }).click()
    await expect(nav.getByRole("link", { name: "Peladas" })).toHaveAttribute("aria-current", "page")
  })

  test("the chat is a page without the tabs, with ← back to the pelada", async ({ page }) => {
    await page.goto(`/pelada/${PELADA_ID}/chat`)
    await expect(page.getByRole("textbox", { name: "Mensagem" })).toBeVisible()
    await expect(page.getByRole("navigation", { name: "Navegação principal" })).toHaveCount(0)
    await page.getByRole("button", { name: "Voltar" }).click()
    await expect(page).toHaveURL(new RegExp(`/pelada/${PELADA_ID}$`))
  })

  test("← on the first page of a visit goes to the parent", async ({ page }) => {
    await page.goto(`/pelada/${PELADA_ID}/membros`)
    await page.getByRole("button", { name: "Voltar" }).click()
    await expect(page).toHaveURL(new RegExp(`/pelada/${PELADA_ID}$`))
    await page.getByRole("button", { name: "Voltar" }).click()
    await expect(page).toHaveURL(/\/peladas$/)
  })
})

test.describe("create menu", () => {
  test("an admin and responsável gets the creations of their pelada", async ({ page }) => {
    await page.goto("/home")
    await page.getByRole("button", { name: "Criar", exact: true }).click()
    const menu = page.getByRole("dialog", { name: "Criar" })
    for (const item of ["Nova sessão", "Convidar jogador", "Nova cobrança", "Criar pelada"]) {
      await expect(menu.getByRole("button", { name: new RegExp(item) })).toBeVisible()
    }
    // a single eligible pelada: straight to its sessions with the form open
    await menu.getByRole("button", { name: /Nova sessão/ }).click()
    await expect(page).toHaveURL(new RegExp(`/pelada/${PELADA_ID}/sessoes$`))
    await expect(page.getByRole("dialog", { name: "Criar sessão" })).toBeVisible()
  })

  test("Nova cobrança opens the creation in the pelada's Financeiro, once", async ({ page }) => {
    await page.goto(`/pelada/${PELADA_ID}/membros`)
    await page.getByRole("button", { name: "Criar", exact: true }).click()
    await page.getByRole("dialog", { name: "Criar" }).getByRole("button", { name: /Nova cobrança/ }).click()
    await expect(page).toHaveURL(new RegExp(`/pelada/${PELADA_ID}/financeiro$`))
    const sheet = page.getByRole("dialog", { name: "Nova cobrança" })
    await expect(sheet).toBeVisible()
    await page.keyboard.press("Escape")
    await expect(sheet).toHaveCount(0)
    // the request was consumed: a reload doesn't open it again
    await page.reload()
    await expect(page.getByRole("button", { name: "Nova cobrança" }).first()).toBeVisible()
    await expect(page.getByRole("dialog", { name: "Nova cobrança" })).toHaveCount(0)
  })

  test("a plain player only gets Criar pelada", async ({ page }) => {
    await page.route(/\/api\/v1\/peladas\/my$/, (route) =>
      route.fulfill({ json: myPeladas.map((p) => ({ ...p, isAdmin: false, isFinanceManager: false })) }),
    )
    await page.goto("/home")
    await page.getByRole("button", { name: "Criar", exact: true }).click()
    const menu = page.getByRole("dialog", { name: "Criar" })
    await expect(menu.getByRole("button", { name: /Criar pelada/ })).toBeVisible()
    await expect(menu.getByRole("button")).toHaveCount(1)
  })
})

test.describe("swipe back in the iPhone's installed app", () => {
  test.use({ userAgent: IOS, hasTouch: true })
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => Object.defineProperty(navigator, "standalone", { value: true }))
  })

  test("a swipe from the edge goes back", async ({ page }) => {
    await page.goto("/peladas")
    await page.getByRole("link", { name: /Pelada do Fut/ }).click()
    await page.getByRole("navigation", { name: "Áreas da pelada" }).getByRole("link", { name: "Ranking" }).click()
    await expect(page.getByRole("heading", { name: "Ranking", level: 1 })).toBeVisible()
    await drag(page, { x: 4, y: 400 }, { x: 300, y: 405 })
    await expect(page).toHaveURL(new RegExp(`/pelada/${PELADA_ID}$`))
  })

  test("the first page of a visit goes to its parent", async ({ page }) => {
    await page.goto(`/pelada/${PELADA_ID}/ranking`)
    await expect(page.getByRole("heading", { name: "Ranking", level: 1 })).toBeVisible()
    await drag(page, { x: 4, y: 400 }, { x: 300, y: 405 })
    await expect(page).toHaveURL(new RegExp(`/pelada/${PELADA_ID}$`))
  })

  test("a swipe that doesn't start at the edge does nothing", async ({ page }) => {
    await page.goto(`/pelada/${PELADA_ID}/ranking`)
    await expect(page.getByRole("heading", { name: "Ranking", level: 1 })).toBeVisible()
    await drag(page, { x: 100, y: 400 }, { x: 360, y: 405 })
    await page.waitForTimeout(400)
    await expect(page).toHaveURL(new RegExp(`/pelada/${PELADA_ID}/ranking$`))
  })
})

test.describe("swipe back on Android", () => {
  test.use({ userAgent: ANDROID, hasTouch: true })

  test("is left to the system", async ({ page }) => {
    await page.addInitScript(() => Object.defineProperty(navigator, "standalone", { value: true }))
    await page.goto(`/pelada/${PELADA_ID}/ranking`)
    await expect(page.getByRole("heading", { name: "Ranking", level: 1 })).toBeVisible()
    await drag(page, { x: 4, y: 400 }, { x: 300, y: 405 })
    await page.waitForTimeout(400)
    await expect(page).toHaveURL(new RegExp(`/pelada/${PELADA_ID}/ranking$`))
  })
})

test.describe("pull to refresh", () => {
  test.use({ hasTouch: true })

  test("reads the page again", async ({ page }) => {
    const reads = counter(page, /\/api\/v1\/peladas\/my$/)
    await page.goto("/peladas")
    await expect(page.getByRole("link", { name: /Pelada do Fut/ })).toBeVisible()
    await page.waitForLoadState("networkidle")
    const before = reads()
    await drag(page, { x: 180, y: 220 }, { x: 180, y: 480 })
    await expect.poll(reads).toBe(before + 1)
  })

  test("a page that registered nothing doesn't react", async ({ page }) => {
    const reads = counter(page, /\/api\/v1\//)
    await page.goto("/settings")
    await expect(page.getByRole("heading", { name: "Configurações", level: 1 })).toBeVisible()
    await page.waitForLoadState("networkidle")
    const before = reads()
    await drag(page, { x: 180, y: 220 }, { x: 180, y: 480 })
    await page.waitForTimeout(500)
    expect(reads()).toBe(before)
  })
})
