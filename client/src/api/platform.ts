import apiClient from "@/api/client"
import type {
  PlatformOverview,
  PlatformPage,
  PlatformPayment,
  PlatformPayments,
  PlatformPeladaDetail,
  PlatformPeladaItem,
  PlatformUserItem,
  SubscriptionAction,
} from "@/types/platform"
import type { SubscriptionPlan, SubscriptionStatus } from "@/types/subscription"

const root = "/api/v1/platform"
export const PLATFORM_PAGE_SIZE = 20

// only the filters that are set go in the query
function params(page: number, filters: Record<string, string | undefined>) {
  const set = Object.fromEntries(Object.entries(filters).filter(([, value]) => value))
  return { ...set, page, size: PLATFORM_PAGE_SIZE }
}

export async function getPlatformOverview(): Promise<PlatformOverview> {
  return (await apiClient.get<PlatformOverview>(`${root}/overview`)).data
}

export interface PlatformPeladasFilter {
  state?: SubscriptionStatus
  q?: string
}

export async function getPlatformPeladas(page = 0, { state, q }: PlatformPeladasFilter = {}): Promise<PlatformPage<PlatformPeladaItem>> {
  const response = await apiClient.get<PlatformPage<PlatformPeladaItem>>(`${root}/peladas`, { params: params(page, { state, q: q?.trim() }) })
  return response.data
}

export async function getPlatformPelada(id: number): Promise<PlatformPeladaDetail> {
  return (await apiClient.get<PlatformPeladaDetail>(`${root}/peladas/${id}`)).data
}

// mirrors PlatformRequests.RecordPayment; amount "19.90", paidOn yyyy-MM-dd
export interface RecordPaymentBody {
  plan: SubscriptionPlan
  amount: string
  paidOn: string
  note: string | null
}

export async function recordPlatformPayment(id: number, body: RecordPaymentBody): Promise<PlatformPeladaDetail> {
  return (await apiClient.post<PlatformPeladaDetail>(`${root}/peladas/${id}/payments`, body)).data
}

// mirrors PlatformRequests.ChangeSubscription: validUntil for SET_VALID_UNTIL, days for EXTEND_DAYS, founder for SET_FOUNDER
export interface ChangeSubscriptionBody {
  action: SubscriptionAction
  validUntil?: string
  days?: number
  founder?: boolean
  note: string | null
}

export async function changePlatformSubscription(id: number, body: ChangeSubscriptionBody): Promise<PlatformPeladaDetail> {
  return (await apiClient.post<PlatformPeladaDetail>(`${root}/peladas/${id}/subscription`, body)).data
}

export async function voidPlatformPayment(paymentId: number, reason: string): Promise<PlatformPayment> {
  return (await apiClient.post<PlatformPayment>(`${root}/payments/${paymentId}/void`, { reason })).data
}

// ISO dates (yyyy-MM-dd), inclusive on paidOn
export interface PlatformPaymentsFilter {
  from?: string
  to?: string
}

export async function getPlatformPayments(page = 0, { from, to }: PlatformPaymentsFilter = {}): Promise<PlatformPayments> {
  return (await apiClient.get<PlatformPayments>(`${root}/payments`, { params: params(page, { from, to }) })).data
}

export async function getPlatformUsers(page = 0, q?: string): Promise<PlatformPage<PlatformUserItem>> {
  const response = await apiClient.get<PlatformPage<PlatformUserItem>>(`${root}/users`, { params: params(page, { q: q?.trim() }) })
  return response.data
}
