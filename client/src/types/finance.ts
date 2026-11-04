export type FinanceClassification = "DAILY" | "MONTHLY" | "OTHER"
export type FinanceListStatus = "DRAFT" | "OPEN" | "CLOSED" | "CANCELED"
export type FinancePaymentMethod = "PIX" | "CASH" | "TRANSFER" | "OTHER"
export type FinanceMovementKind = "OPENING" | "RECEIPT" | "INCOME" | "EXPENSE" | "REFUND" | "REVERSAL"
export type FinanceVisibility = "INDIVIDUAL" | "TRANSPARENT"
// the two list sections, paginated separately by the API (ACTIVE: DRAFT/OPEN, ARCHIVED: CLOSED/CANCELED)
export type FinanceListScope = "ACTIVE" | "ARCHIVED"
export const financeListScopeLabel: Record<FinanceListScope, string> = { ACTIVE: "Em aberto", ARCHIVED: "Encerradas" }
export interface FinanceComponent { name: string; amount: string }
export interface FinanceSettings { visibility: FinanceVisibility; pixKey: string | null; pixRecipient: string | null; qrId: number | null; managerIds: number[] }
export interface FinanceTemplate {
  id: number | null; name: string; classification: FinanceClassification; description: string | null
  components: FinanceComponent[]; pixKey: string | null; pixRecipient: string | null; qrId: number | null; archived: boolean
}
export interface FinanceList extends Omit<FinanceTemplate, "archived" | "id"> {
  id: number; status: FinanceListStatus; dueDate: string | null; dailyId: number | null; dailyLabel: string | null
  publishedAt: string | null; totals: { due: string; received: string; outstanding: string; pendingNotices: number }
}
export interface FinanceOverview {
  revision: number; permissions: { manage: boolean; appointManagers: boolean; viewCash: boolean }
  settings: FinanceSettings; balance: string | null; templates: FinanceTemplate[]; lists: FinanceList[]; hasMore: boolean
  // lists in the requested scope, for numbered pages
  total: number
}
export interface FinanceNotice {
  id: number; userId: number; amount: string; status: "PENDING" | "CONFIRMED" | "REJECTED"; createdAt: string
  resolvedAt: string | null; resolvedBy: number | null; reason: string | null; receiptExpiresAt: string | null; receiptAvailable: boolean
}
export interface FinanceCharge {
  id: number; personId: number; userId: number | null; name: string; components: FinanceComponent[]
  adjustment: string; amountDue: string; received: string; outstanding: string; paymentStatus: "UNPAID" | "PARTIAL" | "PAID"
  overdue: boolean; canceled: boolean; dueDate: string | null; notices: FinanceNotice[]
}
export interface FinanceDetail { collection: FinanceList; charges: FinanceCharge[] }
export interface FinanceResult { overview: FinanceOverview; detail: FinanceDetail | null }
export interface FinanceMovement {
  id: number; listId: number | null; dailyId: number | null; dailyLabel: string | null; chargeId: number | null; noticeId: number | null
  originalId: number | null; kind: FinanceMovementKind; amount: string; obligationDelta: string; effectiveDate: string
  method: FinancePaymentMethod; description: string; category: string | null; createdBy: number; createdAt: string; reversed: boolean
}
export interface FinanceAudit { id: number; actorId: number; actorName: string; operation: FinanceAuditOperation; target: string; reason: string | null; createdAt: string; beforeData: string | null; afterData: string | null }
// total: every item of the query, for numbered pages
export interface FinancePage<T> { items: T[]; hasMore: boolean; total: number }
export interface FinanceCandidate { userId: number; name: string; mensalista: boolean }
export const financeListStatusLabel: Record<FinanceListStatus, string> = { DRAFT: "Rascunho", OPEN: "Aberta", CLOSED: "Encerrada", CANCELED: "Cancelada" }
export const financeVisibilityLabel: Record<FinanceVisibility, string> = { INDIVIDUAL: "Individual", TRANSPARENT: "Transparente" }
export const financeClassificationLabel: Record<FinanceClassification, string> = { DAILY: "Diária", MONTHLY: "Mensalidade", OTHER: "Outra" }
export const financeMethodLabel: Record<FinancePaymentMethod, string> = { PIX: "Pix", CASH: "Dinheiro", TRANSFER: "Transferência", OTHER: "Outro" }
export const financeMovementLabel: Record<FinanceMovementKind, string> = { OPENING: "Saldo inicial", RECEIPT: "Recebimento", INCOME: "Outra entrada", EXPENSE: "Despesa", REFUND: "Devolução", REVERSAL: "Anulação" }
// one status per charge (utils/finance.financeChargeStatus): the row, the list header and the charge details read these maps
export type FinanceChargeStatus = "CANCELED" | "PAID" | "PARTIAL" | "UNPAID"
export const financeChargeStatusLabel: Record<FinanceChargeStatus, string> = { CANCELED: "Cancelada", PAID: "Paga", PARTIAL: "Parcial", UNPAID: "Em aberto" }
// Badge variant="status" classes, like dailyStatusStyle
export const financeChargeStatusStyle: Record<FinanceChargeStatus, string> = {
  CANCELED: "bg-muted text-muted-foreground",
  PAID: "bg-success-muted text-success-soft",
  // its own card fill: warning text only reaches 4.5:1 on card
  PARTIAL: "border-border bg-card text-warning",
  UNPAID: "bg-secondary text-secondary-foreground",
}
export const financeListStatusStyle: Record<FinanceListStatus, string> = {
  DRAFT: "border-border bg-transparent text-muted-foreground",
  OPEN: "bg-brand-muted text-brand-text",
  CLOSED: "bg-secondary text-secondary-foreground",
  CANCELED: "bg-destructive-muted text-destructive-soft",
}
export const financeNoticeLabel: Record<FinanceNotice["status"], string> = { PENDING: "Aguardando conferência", CONFIRMED: "Confirmado", REJECTED: "Rejeitado" }

export type FinanceAuditOperation = "SETTINGS" | "MANAGERS" | "TEMPLATE" | "LIST_SAVE" | "PUBLISH" | "CLOSE" | "REOPEN" | "CANCEL" | "DUPLICATE" | "CHARGE_REPLACE" | "CHARGE_SAVE" | "PARTICIPANTS" | "CHARGE_CANCEL" | "RECEIVE" | "REPORT" | "CONFIRM" | "REJECT" | "CASH" | "REFUND" | "REVERSE" | "QR" | "MANAGERS_REVOKED" | "DAILY_DETACHED"
export const financeAuditLabel: Record<FinanceAuditOperation, string> = {
  SETTINGS: "Configuração alterada",
  MANAGERS: "Responsáveis alterados",
  TEMPLATE: "Modelo salvo",
  LIST_SAVE: "Cobrança salva",
  PUBLISH: "Cobrança publicada",
  CLOSE: "Cobrança encerrada",
  REOPEN: "Cobrança reaberta",
  CANCEL: "Cobrança cancelada",
  DUPLICATE: "Cobrança duplicada",
  CHARGE_REPLACE: "Participante trocado",
  CHARGE_SAVE: "Valor de participante ajustado",
  PARTICIPANTS: "Participantes adicionados",
  CHARGE_CANCEL: "Participante cancelado",
  RECEIVE: "Recebimento registrado",
  REPORT: "Pagamento informado",
  CONFIRM: "Aviso confirmado",
  REJECT: "Aviso rejeitado",
  CASH: "Movimentação registrada",
  REFUND: "Pagamento devolvido",
  REVERSE: "Lançamento anulado",
  QR: "QR Code atualizado",
  MANAGERS_REVOKED: "Responsabilidade revogada",
  DAILY_DETACHED: "Sessão desvinculada",
}

// the Histórico tab's type chips: each group filters the audit by these operations
export type FinanceAuditGroup = "CHARGES" | "PAYMENTS" | "CASH" | "SETTINGS"
export const financeAuditGroupLabel: Record<FinanceAuditGroup, string> = { CHARGES: "Cobranças", PAYMENTS: "Pagamentos", CASH: "Caixa", SETTINGS: "Configurações" }
export const financeAuditGroupOperations: Record<FinanceAuditGroup, FinanceAuditOperation[]> = {
  CHARGES: ["LIST_SAVE", "PUBLISH", "CLOSE", "REOPEN", "CANCEL", "DUPLICATE", "PARTICIPANTS", "CHARGE_SAVE", "CHARGE_REPLACE", "CHARGE_CANCEL"],
  PAYMENTS: ["RECEIVE", "REPORT", "CONFIRM", "REJECT", "REFUND"],
  CASH: ["CASH", "REVERSE"],
  SETTINGS: ["SETTINGS", "MANAGERS", "MANAGERS_REVOKED", "TEMPLATE", "QR", "DAILY_DETACHED"],
}
