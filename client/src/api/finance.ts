import apiClient from "@/api/client"
import type { FinanceCreateValues, FinanceCommand, FinanceListValues, FinanceTemplateValues, FinanceChargeValues, FinanceParticipantsValues, FinanceCashValues, FinanceReceiptValues, FinanceRefundValues } from "@/schemas/finance"
import type { FinanceOverview, FinanceDetail, FinanceResult, FinancePage, FinanceMovement, FinanceAudit, FinanceCandidate, FinanceSettings, FinancePaymentMethod } from "@/types/finance"

const root = (id: number) => `/api/v1/peladas/${id}/finance`
const optional = (value: string) => value || null
export async function getFinance(id: number, page = 0): Promise<FinanceOverview> { return (await apiClient.get<FinanceOverview>(root(id), { params: { page, size: 50 } })).data }
export async function getFinanceList(id: number, listId: number): Promise<FinanceDetail> { return (await apiClient.get<FinanceDetail>(`${root(id)}/lists/${listId}`)).data }
export async function getFinanceMovements(id: number, page = 0): Promise<FinancePage<FinanceMovement>> { return (await apiClient.get<FinancePage<FinanceMovement>>(`${root(id)}/movements`, { params: { page, size: 20 } })).data }
export async function getFinanceAudit(id: number, page = 0): Promise<FinancePage<FinanceAudit>> { return (await apiClient.get<FinancePage<FinanceAudit>>(`${root(id)}/audit`, { params: { page, size: 20 } })).data }
export async function getFinanceCandidates(id: number, dailyId: number): Promise<FinanceCandidate[]> { return (await apiClient.get<FinanceCandidate[]>(`${root(id)}/daily-candidates/${dailyId}`)).data }
async function post(id: number, path: string, body: unknown): Promise<FinanceResult> { return (await apiClient.post<FinanceResult>(`${root(id)}${path}`, body)).data }
async function put(id: number, path: string, body: unknown): Promise<FinanceResult> { return (await apiClient.put<FinanceResult>(`${root(id)}${path}`, body)).data }
export function saveFinanceSettings(id: number, command: FinanceCommand, values: Pick<FinanceSettings, "visibility" | "pixKey" | "pixRecipient" | "qrId">) { return put(id, "/settings", { command, ...values }) }
export function saveFinanceManagers(id: number, command: FinanceCommand, userIds: number[]) { return put(id, "/managers", { command, userIds }) }
export function saveFinanceList(id: number, command: FinanceCommand, values: FinanceListValues, listId: number | null) {
  const data = { ...values, reason: undefined }
  const body = { command, ...data, dueDate: optional(data.dueDate) }
  return listId === null ? post(id, "/lists", body) : put(id, `/lists/${listId}`, body)
}
export function saveFinanceTemplate(id: number, command: FinanceCommand, values: FinanceTemplateValues, templateId: number | null) {
  const data = { ...values, reason: undefined }
  return templateId === null ? post(id, "/templates", { command, ...data }) : put(id, `/templates/${templateId}`, { command, ...data })
}
export type FinanceListAction = "publish" | "close" | "reopen" | "cancel" | "duplicate"
export function financeListAction(id: number, listId: number, action: FinanceListAction, command: FinanceCommand) { return post(id, `/lists/${listId}/${action}`, command) }
export function saveFinanceCharge(id: number, listId: number, chargeId: number, command: FinanceCommand, values: FinanceChargeValues) {
  const data = { ...values, reason: undefined }
  return put(id, `/lists/${listId}/charges/${chargeId}`, { command, ...data, dueDate: optional(data.dueDate) })
}
export function addFinanceParticipants(id: number, listId: number, command: FinanceCommand, values: FinanceParticipantsValues) { return post(id, `/lists/${listId}/participants`, { command, userIds: values.userIds, guestNames: values.guestNames }) }
export function replaceFinanceCharge(id: number, listId: number, chargeId: number, command: FinanceCommand, values: FinanceChargeValues, identity: { userId: number | null; guestName: string | null }) {
  const data = { ...values, reason: undefined }
  return post(id, `/lists/${listId}/charges/${chargeId}/replace`, { command, ...data, ...identity, dueDate: optional(data.dueDate) })
}
export function cancelFinanceCharge(id: number, listId: number, chargeId: number, command: FinanceCommand) { return post(id, `/lists/${listId}/charges/${chargeId}/cancel`, command) }
export function receiveFinance(id: number, listId: number, chargeId: number, command: FinanceCommand, values: FinanceReceiptValues, remaining = false) {
  return post(id, `/lists/${listId}/charges/${chargeId}/receive`, { command, amount: remaining ? null : values.amount, effectiveDate: optional(values.effectiveDate), method: values.method })
}
export function confirmFinanceNotice(id: number, listId: number, chargeId: number, noticeId: number, command: FinanceCommand, values: { effectiveDate: string; method: FinancePaymentMethod }) {
  return post(id, `/lists/${listId}/charges/${chargeId}/notices/${noticeId}/confirm`, { command, effectiveDate: optional(values.effectiveDate), method: values.method })
}
export function rejectFinanceNotice(id: number, listId: number, chargeId: number, noticeId: number, command: FinanceCommand) { return post(id, `/lists/${listId}/charges/${chargeId}/notices/${noticeId}/reject`, command) }
export function refundFinance(id: number, listId: number, chargeId: number, command: FinanceCommand, values: FinanceRefundValues) {
  return post(id, `/lists/${listId}/charges/${chargeId}/refund`, { command, amount: values.amount, method: values.method, effectiveDate: optional(values.effectiveDate), reduceObligation: values.reduceObligation })
}
export function cashFinance(id: number, command: FinanceCommand, values: FinanceCashValues) {
  const data = { ...values, reason: undefined }
  return post(id, "/movements", { command, ...data, effectiveDate: optional(values.effectiveDate) })
}
export function reverseFinanceMovement(id: number, movementId: number, command: FinanceCommand) { return post(id, `/movements/${movementId}/reverse`, command) }
function multipart(command: FinanceCommand, file: File | null) {
  const data = new FormData()
  data.append("command", new Blob([JSON.stringify(command)], { type: "application/json" }))
  if (file) data.append("file", file)
  return data
}
export function reportFinancePayment(id: number, listId: number, chargeId: number, command: FinanceCommand, file: File | null) { return post(id, `/lists/${listId}/charges/${chargeId}/report`, multipart(command, file)) }
export function uploadFinanceQr(id: number, listId: number | null, command: FinanceCommand, file: File) { return post(id, listId === null ? "/qr" : `/lists/${listId}/qr`, multipart(command, file)) }
export async function getFinanceImage(id: number, kind: "qr" | "receipt", imageId: number): Promise<Blob> {
  const path = kind === "qr" ? `/qr/${imageId}` : `/notices/${imageId}/receipt`
  return (await apiClient.get<Blob>(`${root(id)}${path}`, { responseType: "blob" })).data
}
export async function getFinanceNotices(id: number, listId: number, chargeId: number, page: number) {
  return (await apiClient.get<FinancePage<import("@/types/finance").FinanceNotice>>(`${root(id)}/lists/${listId}/charges/${chargeId}/notices`, { params: { page, size: 10 } })).data
}

export function createFinanceCollection(id: number, command: FinanceCommand, values: FinanceCreateValues, publish: boolean) {
  return post(id, "/lists/create", {
    collection: {
      command, name: values.name, classification: values.classification, description: values.description,
      dueDate: optional(values.dueDate), dailyId: values.dailyId,
      components: values.detailedPrice ? values.components : [{ name: "Valor por pessoa", amount: values.amount }],
      pixKey: values.pixKey, pixRecipient: values.pixRecipient, qrId: values.qrId,
    },
    userIds: values.userIds, guestNames: values.guestNames, publish,
  })
}
