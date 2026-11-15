import { test, expect, type Page, type Route } from "@playwright/test"
import { login, mockApi } from "./mockApi"
import { SCHEDULED_ID, CONFIRMED_ID, scheduledDaily, confirmedDaily } from "./fixtures"

// BrowserRouter receives a history navigation without reloading the document.
async function navigate(page: Page, id: number) {
  await page.evaluate(id => {
    history.pushState(null, "", `/daily/${id}`)
    dispatchEvent(new PopStateEvent("popstate"))
  }, id)
}

test.beforeEach(async ({ page }) => {
  await mockApi(page)
  await login(page)
})

test("changing sessions clears the previous data and open dialogs before the new read", async ({ page }) => {
  let pending: Route | undefined
  await page.route(`**/api/v1/dailies/${CONFIRMED_ID}`, route => { pending = route })
  await page.goto(`/daily/${SCHEDULED_ID}`)
  await page.getByRole("button", { name: "Confirmar sessão", exact: true }).click()
  await expect(page.getByRole("alertdialog")).toBeVisible()

  await navigate(page, CONFIRMED_ID)
  await expect.poll(() => !!pending).toBe(true)
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(0)
  await expect(page.getByRole("alertdialog")).toHaveCount(0)
  if (!pending) throw new Error("Expected a pending session read")
  await pending.fulfill({ json: { ...confirmedDaily, dailyDate: "2026-11-06" } })
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Sexta-feira, 6 de novembro")
})

test("a delayed read of the previous session cannot replace the current session", async ({ page }) => {
  let pending: Route | undefined
  await page.route(`**/api/v1/dailies/${SCHEDULED_ID}`, route => { pending = route })
  await page.goto(`/daily/${SCHEDULED_ID}`)
  await expect.poll(() => !!pending).toBe(true)
  await navigate(page, CONFIRMED_ID)
  await expect(page.getByRole("button", { name: "Iniciar sessão", exact: true })).toBeVisible()
  if (!pending) throw new Error("Expected a pending session read")
  await pending.fulfill({ json: scheduledDaily })
  // The response has reached the browser before checking the retained session state.
  await page.waitForLoadState("networkidle")
  await expect(page.getByRole("button", { name: "Iniciar sessão", exact: true })).toBeVisible()
  await expect(page.getByRole("button", { name: "Confirmar sessão", exact: true })).toHaveCount(0)
})

test("denied access to one session does not survive navigation to another", async ({ page }) => {
  await page.route(`**/api/v1/dailies/${SCHEDULED_ID}`, route => route.fulfill({ status: 403, json: { message: "Sem acesso" } }))
  await page.goto(`/daily/${SCHEDULED_ID}`)
  await expect(page.getByRole("heading", { name: "Acesso negado" })).toBeVisible()
  await navigate(page, CONFIRMED_ID)
  await expect(page.getByRole("button", { name: "Iniciar sessão", exact: true })).toBeVisible()
  await expect(page.getByRole("heading", { name: "Acesso negado" })).toHaveCount(0)
})

test("failed detail reads show an actionable error and can recover", async ({ page }) => {
  let fail = true
  await page.route(`**/api/v1/dailies/${SCHEDULED_ID}`, route => fail
    ? route.fulfill({ status: 500, json: { message: "Sessão temporariamente indisponível" } })
    : route.fulfill({ json: scheduledDaily }))
  await page.goto(`/daily/${SCHEDULED_ID}`)
  await expect(page.getByRole("alert")).toContainText("Sessão temporariamente indisponível")
  await expect(page.getByRole("button", { name: "Confirmar sessão", exact: true })).toHaveCount(0)
  fail = false
  await page.getByRole("button", { name: "Tentar novamente", exact: true }).click()
  await expect(page.getByRole("button", { name: "Confirmar sessão", exact: true })).toBeVisible()
})

test("an older failed retry cannot replace a newer successful read", async ({ page }) => {
  let reads = 0
  let older: Route | undefined
  await page.route(`**/api/v1/dailies/${SCHEDULED_ID}`, route => {
    reads++
    if (reads === 1) return route.fulfill({ status: 500, json: { message: "Tente ler novamente" } })
    if (reads === 2) { older = route; return }
    return route.fulfill({ json: scheduledDaily })
  })
  await page.goto(`/daily/${SCHEDULED_ID}`)
  const retry = page.getByRole("button", { name: "Tentar novamente", exact: true })
  await retry.click()
  await expect.poll(() => !!older).toBe(true)
  await retry.click()
  await expect(page.getByRole("button", { name: "Confirmar sessão", exact: true })).toBeVisible()
  if (!older) throw new Error("Expected an older pending retry")
  await older.fulfill({ status: 500, json: { message: "Resposta antiga" } })
  await page.waitForLoadState("networkidle")
  await expect(page.getByRole("button", { name: "Confirmar sessão", exact: true })).toBeVisible()
  await expect(retry).toHaveCount(0)
})
