import { test, expect, type Page, type Route } from "@playwright/test"
import { mockApi, login } from "./mockApi"
import { financeMemberDetail, financeMemberOverview, financeOverview, peladaDetail, readOnlySubscription, me } from "./fixtures"
import type { FinanceDetail } from "../src/types/finance"

const path = `/pelada/${peladaDetail.id}/financeiro/50/paguei`
const action = (page: Page) => page.getByRole("button", { name: "Paguei — avisar responsável" })
const notice = { id: 999, userId: me.id, amount: "50.00", status: "PENDING" as const, createdAt: "2026-11-14T12:00:00Z", resolvedAt: null, resolvedBy: null, reason: null, receiptExpiresAt: null, receiptAvailable: false }

async function setup(page: Page, options: { detail?: FinanceDetail; manager?: boolean; readonly?: boolean; signedIn?: boolean } = {}) {
  const unmatched = await mockApi(page)
  if (options.signedIn !== false) await login(page)
  let detail = structuredClone(options.detail ?? financeMemberDetail)
  let revision = 3
  const reports: { url: string; command: { revision: number; requestKey: string }; body: string }[] = []
  let onReport: ((route: Route) => Promise<void>) | null = null
  await page.route(/\/finance(?:\?.*)?$/, route => route.fulfill({ json: { ...(options.manager ? financeOverview : financeMemberOverview), revision } }))
  await page.route(/\/finance\/lists\/\d+$/, route => route.fulfill({ json: detail }))
  if (options.readonly) await page.route(/\/api\/v1\/peladas\/\d+$/, route => route.fulfill({ json: { ...peladaDetail, subscription: readOnlySubscription } }))
  await page.route(/\/charges\/\d+\/report$/, async route => {
    const body = route.request().postDataBuffer()!.toString()
    const command = JSON.parse(body.match(/\{"revision":\d+,"requestKey":"[^"]+","reason":null\}/)![0])
    reports.push({ url: route.request().url(), command, body })
    if (onReport) return onReport(route)
    detail.charges = detail.charges.map(c => c.userId === me.id ? { ...c, notices: [notice] } : c)
    revision++
    await route.fulfill({ json: { overview: { ...financeMemberOverview, revision }, detail } })
  })
  return { reports, unmatched, setDetail: (next: FinanceDetail) => { detail = next; revision++ }, onReport: (handler: (route: Route) => Promise<void>) => { onReport = handler } }
}

test("quick report reads only, sends own charge once, keeps full detail link", async ({ page }, info) => {
  const state = await setup(page)
  await page.goto(path)
  await expect(action(page)).toBeVisible()
  await expect(page.getByRole("tab")).toHaveCount(0)
  await expect(page.getByRole("main").last()).toContainText(me.username)
  expect(state.reports).toHaveLength(0)
  await page.reload()
  await expect(action(page)).toBeVisible()
  expect(state.reports).toHaveLength(0)
  expect(await action(page).evaluate(el => el.getBoundingClientRect().bottom <= window.innerHeight)).toBe(true)
  await page.screenshot({ path: `../.taste-skill/finance-quick-report/${info.project.name}.png`, fullPage: true })
  await action(page).focus()
  await page.keyboard.press("Enter")
  await expect(page.getByRole("status")).toContainText("Pagamento informado. Aguardando conferência.")
  expect(state.reports).toHaveLength(1)
  expect(state.reports[0].url).toContain(`/charges/${financeMemberDetail.charges[0].id}/report`)
  expect(state.reports[0].command.revision).toBe(3)
  expect(state.reports[0].body).not.toContain('name="file"')
  await expect(page.getByRole("dialog")).toHaveCount(0)
  await page.getByRole("link", { name: "Ver cobrança completa" }).click()
  await expect(page).toHaveURL(path.replace("/paguei", ""))
  expect(state.unmatched).toEqual([])
})

for (const state of ["pending", "paid", "no-charge", "canceled", "closed", "draft", "canceled-list", "readonly"] as const) {
  test(`quick report blocks ${state}`, async ({ page }) => {
    const detail = structuredClone(financeMemberDetail)
    if (state === "pending") detail.charges[0].notices = [notice]
    if (state === "paid") Object.assign(detail.charges[0], { paymentStatus: "PAID", outstanding: "0.00" })
    if (state === "no-charge") detail.charges[0].userId = null // same name must never claim a guest
    if (state === "canceled") detail.charges[0].canceled = true
    if (state === "closed") detail.collection.status = "CLOSED"
    if (state === "draft") detail.collection.status = "DRAFT"
    if (state === "canceled-list") detail.collection.status = "CANCELED"
    const s = await setup(page, { detail, readonly: state === "readonly" })
    await page.goto(path)
    await expect(page.getByRole("main").last().getByRole("status")).toBeVisible()
    await expect(action(page)).toHaveCount(0)
    expect(s.reports).toHaveLength(0)
  })
}

test("manager reports own charge, never the first other player", async ({ page }) => {
  const detail = structuredClone(financeMemberDetail)
  detail.charges.unshift({ ...detail.charges[0], id: 123, userId: me.id + 1, name: "Outro jogador" })
  const s = await setup(page, { detail, manager: true })
  await page.goto(path)
  await action(page).click()
  await expect(page.getByRole("status")).toContainText("Pagamento informado")
  expect(s.reports[0].url).toContain(`/charges/${financeMemberDetail.charges[0].id}/report`)
  await expect(page.getByRole("main").last()).not.toContainText("Outro jogador")
})

test("uncertain retry keeps command key and duplicate clicks are blocked", async ({ page }) => {
  const s = await setup(page)
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  s.onReport(async route => { await gate; await route.fulfill({ status: 503, json: { message: "Tente novamente" } }) })
  await page.goto(path)
  await action(page).click()
  await expect(page.getByRole("button", { name: "Enviando…" })).toBeDisabled()
  expect(s.reports).toHaveLength(1)
  release()
  await expect(page.getByRole("alert")).toContainText("Tente novamente")
  await action(page).click()
  await expect.poll(() => s.reports.length).toBe(2)
  expect(s.reports[1].command).toEqual(s.reports[0].command)
})

test("conflict refresh shows new amount and requires a fresh explicit click", async ({ page }) => {
  const s = await setup(page)
  s.onReport(async route => {
    const detail = structuredClone(financeMemberDetail)
    detail.charges[0].outstanding = "70.00"
    s.setDetail(detail)
    await route.fulfill({ status: 409, json: { message: "O financeiro mudou. Atualize a tela e tente novamente" } })
  })
  await page.goto(path)
  await action(page).click()
  await expect(page.getByRole("main").last()).toContainText("R$ 70,00")
  await expect(action(page)).toBeEnabled()
  expect(s.reports).toHaveLength(1)
  await action(page).click()
  await expect.poll(() => s.reports.length).toBe(2)
  expect(s.reports[1].command.revision).toBe(4)
  expect(s.reports[1].command.requestKey).not.toBe(s.reports[0].command.requestKey)
})

test("optional receipt validates format and size, then sends valid image", async ({ page }) => {
  const s = await setup(page)
  await page.goto(path)
  await page.getByRole("button", { name: "Anexar comprovante (opcional)" }).click()
  const file = page.getByLabel("Comprovante", { exact: true })
  await file.setInputFiles({ name: "receipt.pdf", mimeType: "application/pdf", buffer: Buffer.from("bad") })
  await action(page).click()
  await expect(page.getByText("Use JPEG ou PNG")).toBeVisible()
  expect(s.reports).toHaveLength(0)
  await file.setInputFiles({ name: "big.png", mimeType: "image/png", buffer: Buffer.alloc(5 * 1024 * 1024 + 1) })
  await action(page).click()
  await expect(page.getByText("Máximo de 5 MB")).toBeVisible()
  await file.setInputFiles("e2e/assets/qr.png")
  await action(page).click()
  await expect(page.getByRole("status")).toContainText("Pagamento informado")
  expect(s.reports[0].body).toContain('name="file"')
})

test("read failure hides action and offers retry", async ({ page }) => {
  const s = await setup(page)
  let failed = false
  await page.route(/\/finance\/lists\/\d+$/, async route => {
    if (failed) return route.fulfill({ json: financeMemberDetail })
    failed = true
    await route.fulfill({ status: 403, json: { message: "Acesso negado" } })
  })
  await page.goto(path)
  await expect(page.getByRole("alert")).toContainText("Acesso negado")
  await expect(action(page)).toHaveCount(0)
  await page.getByRole("button", { name: "Tentar novamente" }).click()
  await expect(action(page)).toBeVisible()
  expect(s.reports).toHaveLength(0)
})

test("login returns to quick destination without submitting", async ({ page }) => {
  const s = await setup(page, { signedIn: false })
  await page.route(/\/auth\/login$/, route => route.fulfill({ json: { token: "test", user: me } }))
  await page.goto(path)
  await expect(page).toHaveURL(/\/auth$/)
  await page.getByLabel("E-mail", { exact: true }).fill("jogador@example.test")
  await page.getByLabel("Senha", { exact: true }).fill("senha123")
  await page.getByRole("button", { name: "Entrar", exact: true }).click()
  await expect(page).toHaveURL(path)
  await expect(action(page)).toBeVisible()
  expect(s.reports).toHaveLength(0)
})

test("shared message points to quick destination, full detail remains reachable", async ({ page }) => {
  await setup(page, { manager: true })
  await page.goto(path.replace("/paguei", ""))
  await page.getByRole("button", { name: "Opções da cobrança" }).click()
  await page.getByRole("menuitem", { name: "Compartilhar no WhatsApp" }).click()
  await expect(page.getByRole("dialog").getByLabel("Mensagem")).toHaveValue(new RegExp(`✅ Já pagou\\? Avise aqui: .*${path}$`))
})

test("changing collection hides previous account data until reads finish", async ({ page }) => {
  const s = await setup(page)
  await page.goto(path)
  await expect(action(page)).toBeVisible()
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route(/\/finance\/lists\/51$/, async route => { await gate; await route.fulfill({ json: { ...financeMemberDetail, collection: { ...financeMemberDetail.collection, id: 51, name: "Outra cobrança" }, charges: [] } }) })
  await page.evaluate((next) => { history.pushState({}, "", next); dispatchEvent(new PopStateEvent("popstate")) }, path.replace("/50/", "/51/"))
  await expect(page.getByRole("status", { name: "Carregando cobrança" })).toBeVisible()
  await expect(action(page)).toHaveCount(0)
  release()
  await expect(page.getByText("Não há cobrança ativa para sua conta.")).toBeVisible()
  expect(s.reports).toHaveLength(0)
})
