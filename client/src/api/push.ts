import apiClient from "./client"
import type { PushSubscriptionRequest } from "@/schemas/push"
import type { VapidPublicKey } from "@/types/push"

export const PUSH_REMOVE_PATH = "/api/v1/push/subscriptions/remove"

// null (204) when the server has no VAPID key: push is off
export async function getVapidPublicKey(): Promise<string | null> {
  const response = await apiClient.get<VapidPublicKey | "">("/api/v1/push/vapid-public-key")
  return response.status === 204 || !response.data ? null : response.data.publicKey
}

// Upsert by endpoint: a device another account used moves to the caller
export async function subscribePush(subscription: PushSubscriptionRequest): Promise<void> {
  await apiClient.post("/api/v1/push/subscriptions", subscription)
}

// Removes it only when it is the caller's. `token` sends the request for a session that is being closed
// (logout clears the stored token at once); `timeout` keeps the logout from waiting on a slow network
export async function unsubscribePush(endpoint: string, options: { token?: string; timeout?: number } = {}): Promise<void> {
  await apiClient.post(
    PUSH_REMOVE_PATH,
    { endpoint },
    {
      timeout: options.timeout,
      headers: options.token ? { Authorization: `Bearer ${options.token}` } : undefined,
    },
  )
}
