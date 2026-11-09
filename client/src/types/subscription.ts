// mirrors SubscriptionStatus: the effective state of a pelada's subscription, computed by the server
export type SubscriptionStatus = "TRIAL" | "ACTIVE" | "PAST_DUE" | "READ_ONLY" | "PENDING" | "EXEMPT"

// mirrors SubscriptionSummaryDTO
export interface SubscriptionSummary {
  status: SubscriptionStatus
  // ISO instant: end of the trial or of the paid period (the grace runs 7 days past it); null for PENDING / EXEMPT
  validUntil: string | null
  // whether sporting and financial data can be written (the server re-checks every command, 402)
  writable: boolean
}

// mirrors SubscriptionKind: what was recorded (the status above is computed from it and the validity)
export type SubscriptionKind = "TRIAL" | "PENDING" | "PAID" | "EXEMPT" | "CANCELED"

// mirrors SubscriptionPlan
export type SubscriptionPlan = "MONTHLY" | "ANNUAL"

export const subscriptionStatusLabel: Record<SubscriptionStatus, string> = {
  TRIAL: "Teste grátis",
  ACTIVE: "Ativa",
  PAST_DUE: "Em atraso",
  READ_ONLY: "Somente leitura",
  PENDING: "Pendente",
  EXEMPT: "Isenta",
}

// Badge colors (Badge variant="status")
export const subscriptionStatusStyle: Record<SubscriptionStatus, string> = {
  TRIAL: "bg-brand-muted text-brand-text",
  ACTIVE: "bg-success-muted text-success-soft",
  // its own card fill: warning text only reaches 4.5:1 on card
  PAST_DUE: "border-border bg-card text-warning",
  READ_ONLY: "bg-destructive-muted text-destructive-soft",
  PENDING: "border-border bg-transparent text-muted-foreground",
  EXEMPT: "bg-secondary text-secondary-foreground",
}

export const subscriptionKindLabel: Record<SubscriptionKind, string> = {
  TRIAL: "Teste grátis",
  PENDING: "Pendente",
  PAID: "Paga",
  EXEMPT: "Isenta",
  CANCELED: "Cancelada",
}

export const subscriptionPlanLabel: Record<SubscriptionPlan, string> = {
  MONTHLY: "Mensal",
  ANNUAL: "Anual",
}

// mirrors PeladaSubscriptionDTO.PlanOffer: a list price for the pelada's member count and its Pix BR Code
export interface SubscriptionPlanOffer {
  plan: SubscriptionPlan
  // exact two-decimal string, "19.90"
  amount: string
  // the "Pix Copia e Cola" (also what the QR Code holds); null when the platform has no Pix configured
  pixCode: string | null
}

// mirrors PeladaSubscriptionDTO.Payment
export interface CreatorSubscriptionPayment {
  // yyyy-MM-dd
  paidOn: string
  amount: string
  plan: SubscriptionPlan
  // ISO instant: the end of the period it paid for
  periodEnd: string
}

// mirrors PeladaSubscriptionDTO: the creator's Assinatura area (GET /peladas/{id}/subscription)
export interface PeladaSubscription {
  status: SubscriptionStatus
  validUntil: string | null
  plan: SubscriptionPlan | null
  founder: boolean
  // the creator said they paid; cleared when the platform admin records the payment
  pixClaimedAt: string | null
  claimedPlan: SubscriptionPlan | null
  // who receives the Pix (full name); null when the platform has no Pix configured
  pixRecipient: string | null
  plans: SubscriptionPlanOffer[]
  // latest first, voided ones left out
  payments: CreatorSubscriptionPayment[]
}
