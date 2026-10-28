import apiClient from "./client"
import type { NotificationPreference, NotificationResponse, UnreadCountResponse } from "@/types/notification"

export const NOTIFICATIONS_PAGE_SIZE = 20

export interface NotificationsQuery {
  page: number
  size?: number
  // only unread notifications ("Não lidas")
  unread?: boolean
}

// The caller's notifications, newest first
export async function getNotifications({ page, size = NOTIFICATIONS_PAGE_SIZE, unread = false }: NotificationsQuery): Promise<NotificationResponse[]> {
  const response = await apiClient.get<NotificationResponse[]>("/api/v1/notifications", {
    params: { page, size, unread },
  })
  return response.data
}

export async function getUnreadNotificationCount(): Promise<number> {
  const response = await apiClient.get<UnreadCountResponse>("/api/v1/notifications/unread-count")
  return response.data.count
}

// Idempotent; reading a "perdeu a vaga" notification also dismisses the bump alert
export async function markNotificationRead(id: number): Promise<void> {
  await apiClient.post(`/api/v1/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiClient.post("/api/v1/notifications/read-all")
}

// E-mail per category (in-app is always on); always the four categories
export async function getNotificationPreferences(): Promise<NotificationPreference[]> {
  const response = await apiClient.get<NotificationPreference[]>("/api/v1/notifications/preferences")
  return response.data
}

// Partial: only the categories sent change; returns all four
export async function updateNotificationPreferences(changes: NotificationPreference[]): Promise<NotificationPreference[]> {
  const response = await apiClient.put<NotificationPreference[]>("/api/v1/notifications/preferences", changes)
  return response.data
}

// Public (the e-mail's "Desativar" link): the token says which user and category; works logged out
export async function unsubscribeFromEmails(token: string): Promise<NotificationPreference> {
  const response = await apiClient.post<NotificationPreference>("/api/v1/notifications/unsubscribe", { token })
  return response.data
}

export async function resubscribeToEmails(token: string): Promise<NotificationPreference> {
  const response = await apiClient.post<NotificationPreference>("/api/v1/notifications/resubscribe", { token })
  return response.data
}
