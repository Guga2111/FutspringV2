import { beforeEach, describe, expect, it, vi } from "vitest"
vi.mock("@/api/client", () => ({ default: { post: vi.fn(), get: vi.fn(), put: vi.fn() } }))
import apiClient from "@/api/client"
import { receiveFinance, reportFinancePayment, saveFinanceList, confirmFinanceNotice, getFinanceImage, refundFinance, getFinance, getFinanceMovements } from "@/api/finance"
const command = { revision: 12, requestKey: "ed51d453-c4b5-407e-9709-99348e9fca42", reason: null }
beforeEach(() => { vi.resetAllMocks(); vi.mocked(apiClient.post).mockResolvedValue({ data: {} }); vi.mocked(apiClient.get).mockResolvedValue({ data: new Blob() }); vi.mocked(apiClient.put).mockResolvedValue({ data: {} }) })
describe("finance API", () => {
  it("asks for one scope and one numbered page at a time", async () => {
    await getFinance(1, "ARCHIVED", 2)
    expect(apiClient.get).toHaveBeenLastCalledWith("/api/v1/peladas/1/finance", { params: { scope: "ARCHIVED", page: 2, size: 10 } })
    await getFinanceMovements(1, 3)
    expect(apiClient.get).toHaveBeenLastCalledWith("/api/v1/peladas/1/finance/movements", { params: { page: 3, size: 20 } })
  })
  it("lets the server capture the remaining balance for mark-paid", async () => {
    await receiveFinance(1, 2, 3, command, { amount: "40.00", effectiveDate: "", method: "PIX", reason: "" }, true)
    expect(apiClient.post).toHaveBeenCalledWith("/api/v1/peladas/1/finance/lists/2/charges/3/receive", { command, amount: null, effectiveDate: null, method: "PIX" })
  })
  it("preserves strings and scopes confirmations and refunds", async () => {
    await confirmFinanceNotice(1, 2, 3, 4, command, { effectiveDate: "2026-10-30", method: "CASH" })
    expect(apiClient.post).toHaveBeenLastCalledWith("/api/v1/peladas/1/finance/lists/2/charges/3/notices/4/confirm", { command, effectiveDate: "2026-10-30", method: "CASH" })
    await refundFinance(1, 2, 3, command, { amount: "10.00", effectiveDate: "", method: "PIX", reason: "Devolução", reduceObligation: true })
    expect(apiClient.post).toHaveBeenLastCalledWith("/api/v1/peladas/1/finance/lists/2/charges/3/refund", { command, amount: "10.00", effectiveDate: null, method: "PIX", reduceObligation: true })
  })
  it("reports no caller-supplied amount, identity or timestamp", async () => {
    const file = new File(["proof"], "p.png", { type: "image/png" })
    await reportFinancePayment(1, 2, 3, command, file)
    const [url, body] = vi.mocked(apiClient.post).mock.calls[0]
    expect(url).toBe("/api/v1/peladas/1/finance/lists/2/charges/3/report")
    expect(body).toBeInstanceOf(FormData)
    const form = body as FormData
    expect(form.get("file")).toBe(file)
    expect(form.get("amount")).toBeNull()
    expect(JSON.parse(await (form.get("command") as Blob).text())).toEqual(command)
  })
  it("loads protected images through the authenticated API", async () => {
    await getFinanceImage(1, "receipt", 4)
    expect(apiClient.get).toHaveBeenCalledWith("/api/v1/peladas/1/finance/notices/4/receipt", { responseType: "blob" })
  })
  it("serializes optional dates as null while keeping exact list values", async () => {
    await saveFinanceList(1, command, { name: "Evento", classification: "OTHER", description: "", dueDate: "", dailyId: null, components: [{ name: "Campo", amount: "45.00" }], pixKey: "", pixRecipient: "", qrId: null, reason: "" }, null)
    expect(apiClient.post).toHaveBeenCalledWith("/api/v1/peladas/1/finance/lists", expect.objectContaining({ dueDate: null, components: [{ name: "Campo", amount: "45.00" }] }))
  })
})

it("creates the collection, allocations and publication in one command without UI-only fields", async () => {
  const { createFinanceCollection } = await import("@/api/finance")
  await createFinanceCollection(1, command, { name: "Diária", classification: "DAILY", amount: "45.00", detailedPrice: false, description: "", dueDate: "", dailyId: null, components: [{ name: "Campo", amount: "0.00" }], pixKey: "pix", pixRecipient: "Gestor", qrId: null, reason: "", userIds: [2, 3], guestNames: ["Convidado"] }, true)
  expect(apiClient.post).toHaveBeenCalledWith("/api/v1/peladas/1/finance/lists/create", {
    collection: { command, name: "Diária", classification: "DAILY", description: "", dueDate: null, dailyId: null, components: [{ name: "Valor por pessoa", amount: "45.00" }], pixKey: "pix", pixRecipient: "Gestor", qrId: null },
    userIds: [2, 3], guestNames: ["Convidado"], publish: true,
  })
})
