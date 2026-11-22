import type { SubscriptionPlan, SubscriptionStatus, SubscriptionSummary } from "@/types/subscription"
import { formatShortDate, todayInZone } from "@/utils/dates"
import { formatFinanceMoney } from "@/utils/finance"

const DAY_MS = 86_400_000

// the creator sees the trial's end from this many days before it
export const TRIAL_NOTICE_DAYS = 5
// a paid subscription keeps writing this long after validUntil (mirrors SubscriptionState.GRACE)
export const GRACE_DAYS = 7

// List prices in cents, mirroring SubscriptionPlans.java (change both together); the price a
// pelada pays comes from the API, these only feed the public landing page
export const PLAN_PRICE_CENTS: Record<"standard" | "large", Record<SubscriptionPlan, number>> = {
  standard: { MONTHLY: 1990, ANNUAL: 19900 },
  large: { MONTHLY: 2990, ANNUAL: 29900 },
}
// peladas with more members than this pay the large price (SubscriptionPlans.LARGE_GROUP_MEMBERS)
export const LARGE_GROUP_MEMBERS = 40
// the first pelada of an account (SubscriptionService.TRIAL_LENGTH, SubscriptionGuard.TRIAL_MEMBER_CAP)
export const TRIAL_DAYS = 30
export const TRIAL_MEMBER_CAP = 30

export interface LandingPricing {
  large: boolean
  // up to the trial's member cap, a new pelada fits in the free trial
  fitsTrial: boolean
  // formatted: "R$ 19,90"
  monthly: string
  annual: string
  // the annual price over 12 months, rounded to the cent
  annualPerMonth: string
  // 12 monthly payments minus the annual one
  annualSaving: string
}

const money = (cents: number) => formatFinanceMoney(`${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, "0")}`)

// The landing's price for a pelada of `members`, in integer cents (no floating point)
export function landingPricing(members: number): LandingPricing {
  const large = members > LARGE_GROUP_MEMBERS
  const { MONTHLY, ANNUAL } = PLAN_PRICE_CENTS[large ? "large" : "standard"]
  return {
    large,
    fitsTrial: members <= TRIAL_MEMBER_CAP,
    monthly: money(MONTHLY),
    annual: money(ANNUAL),
    annualPerMonth: money(Math.round(ANNUAL / 12)),
    annualSaving: money(MONTHLY * 12 - ANNUAL),
  }
}

// the hint of every button disabled by a read-only pelada
export const READ_ONLY_HINT = "Pelada em somente leitura"

export interface SubscriptionNotice {
  text: string
  // the creator's button ("Assinar", "Renovar"); null for members
  action: string | null
  // read-only (the destructive alert) or a warning before it
  readOnly: boolean
}

// A pelada without a subscription row (null) writes, like the server's guard
export function isWritable(subscription: SubscriptionSummary | null | undefined): boolean {
  return subscription?.writable ?? true
}

// When the trial notice starts showing, for useNowUntil to show it on time; null when no notice is pending
export function subscriptionNoticeFrom(subscription: SubscriptionSummary | null): string | null {
  if (subscription?.status !== "TRIAL" || !subscription.validUntil) return null
  return new Date(new Date(subscription.validUntil).getTime() - TRIAL_NOTICE_DAYS * DAY_MS).toISOString()
}

interface NoticeInput {
  validUntil: string | null
  isCreator: boolean
  now: Date
  // a date in the pelada's zone: "07/12"
  day: (instant: number) => string
}

const readOnlyNotice = ({ isCreator }: NoticeInput): SubscriptionNotice =>
  isCreator
    ? { text: "Pelada em somente leitura", action: "Assinar", readOnly: true }
    : { text: "Pelada em somente leitura: o criador precisa renovar a assinatura", action: null, readOnly: true }

const NOTICES: Record<SubscriptionStatus, (input: NoticeInput) => SubscriptionNotice | null> = {
  TRIAL: ({ validUntil, isCreator, now, day }) => {
    if (!isCreator || !validUntil) return null
    const end = new Date(validUntil).getTime()
    if (end - now.getTime() > TRIAL_NOTICE_DAYS * DAY_MS) return null
    return { text: `Teste grátis até ${day(end)}`, action: "Assinar", readOnly: false }
  },
  PAST_DUE: ({ validUntil, isCreator, day }) =>
    isCreator && validUntil
      ? {
          text: `Pagamento pendente: a pelada trava em ${day(new Date(validUntil).getTime() + GRACE_DAYS * DAY_MS)}`,
          action: "Renovar",
          readOnly: false,
        }
      : null,
  READ_ONLY: readOnlyNotice,
  PENDING: readOnlyNotice,
  ACTIVE: () => null,
  EXEMPT: () => null,
}

// The banner of the pelada's pages (and the session page) for the caller's role, or null when there's nothing to say
export function subscriptionNotice(
  subscription: SubscriptionSummary | null,
  isCreator: boolean,
  now: Date,
  timeZone: string,
): SubscriptionNotice | null {
  if (!subscription) return null
  const day = (instant: number) => formatShortDate(todayInZone(timeZone, new Date(instant)), now)
  return NOTICES[subscription.status]({ validUntil: subscription.validUntil, isCreator, now, day })
}
