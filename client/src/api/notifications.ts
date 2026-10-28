import apiClient from "./client"
import type { NotificationResponse, UnreadCountResponse } from "@/types/notification"

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
