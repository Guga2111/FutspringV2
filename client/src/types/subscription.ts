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
