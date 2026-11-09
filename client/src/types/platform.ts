// mirrors PlatformViews (/api/v1/platform/**): the platform admin's panel. Money is an exact two-decimal string ("19.90")
import type { SubscriptionKind, SubscriptionPlan, SubscriptionStatus } from "@/types/subscription"
import type { PublicUser } from "@/types/user"

export interface PlatformPage<T> {
  items: T[]
  total: number
}

// a pelada that needs the operator; date = the trial's end, or when the Pix was claimed
export interface PlatformAttention {
  id: number
  name: string
  date: string
}

export interface MonthRevenue {
  // yyyy-MM
  month: string
  total: string
}

export interface PlatformOverview {
  // every effective state, zeros included
  counts: Record<SubscriptionStatus, number>
  trialsEndingSoon: PlatformAttention[]
  pixClaimed: PlatformAttention[]
  revenueThisMonth: string
  // the last 12 months, the current one last
  revenueByMonth: MonthRevenue[]
  recurringEstimate: string
  users: { total: number; last30Days: number }
}

export interface PlatformPeladaItem {
  id: number
  name: string
  creator: PublicUser
  memberCount: number
  status: SubscriptionStatus
  validUntil: string | null
  founder: boolean
  pixClaimedAt: string | null
  // yyyy-MM-dd of the last finished session
  lastSessionDate: string | null
}

export interface PlatformPrice {
  plan: SubscriptionPlan
  amount: string
}

export interface PlatformSubscription {
  kind: SubscriptionKind
  status: SubscriptionStatus
  writable: boolean
  validUntil: string | null
  plan: SubscriptionPlan | null
  founder: boolean
  pixClaimedAt: string | null
  claimedPlan: SubscriptionPlan | null
  // list prices for the pelada's member count (the payment form's default amount)
  prices: PlatformPrice[]
  createdAt: string
  updatedAt: string
}

export interface PlatformPayment {
  id: number
  // null once the pelada was deleted (the names are snapshots)
  peladaId: number | null
  peladaName: string
  payerId: number | null
  payerName: string
  amount: string
  // yyyy-MM-dd
  paidOn: string
  plan: SubscriptionPlan
  periodStart: string
  periodEnd: string
  recordedBy: PublicUser | null
  note: string | null
  voidedAt: string | null
  voidReason: string | null
  createdAt: string
}

export interface PlatformPayments {
  items: PlatformPayment[]
  total: number
  // the period's payments that aren't voided
  sum: string
}

// mirrors SubscriptionEventType
export type SubscriptionEventType =
  | "CREATED"
  | "PAYMENT_RECORDED"
  | "PAYMENT_VOIDED"
  | "VALID_UNTIL_SET"
  | "DAYS_EXTENDED"
  | "EXEMPTED"
  | "CANCELED"
  | "FOUNDER_CHANGED"
  | "PIX_CLAIMED"

export const subscriptionEventLabel: Record<SubscriptionEventType, string> = {
  CREATED: "Assinatura criada",
  PAYMENT_RECORDED: "Pagamento registrado",
  PAYMENT_VOIDED: "Pagamento estornado",
  VALID_UNTIL_SET: "Validade definida",
  DAYS_EXTENDED: "Dias estendidos",
  EXEMPTED: "Isentada",
  CANCELED: "Cancelada",
  FOUNDER_CHANGED: "Fundadora alterada",
  PIX_CLAIMED: "Pix informado",
}

export interface PlatformEvent {
  id: number
  type: SubscriptionEventType
  // null = the system
  actor: PublicUser | null
  kindBefore: SubscriptionKind | null
  kindAfter: SubscriptionKind | null
  validUntilBefore: string | null
  validUntilAfter: string | null
  note: string | null
  createdAt: string
}

export interface PlatformPeladaDetail {
  pelada: {
    id: number
    name: string
    image: string | null
    creator: PublicUser
    creatorEmail: string
    memberCount: number
    lastSessionDate: string | null
  }
  // null for a pelada without a subscription row (none in production)
  subscription: PlatformSubscription | null
  // the latest 50, voided included
  payments: PlatformPayment[]
  // the latest 50, newest first
  events: PlatformEvent[]
}

export interface PlatformUserItem {
  id: number
  username: string
  email: string
  // null for accounts created before V16
  createdAt: string | null
  peladasCreated: { id: number; name: string }[]
  memberOf: number
  trialUsedAt: string | null
  isDeleted: boolean
}

// mirrors PlatformRequests.SubscriptionAction
export type SubscriptionAction = "SET_VALID_UNTIL" | "EXTEND_DAYS" | "EXEMPT" | "CANCEL" | "SET_FOUNDER"
