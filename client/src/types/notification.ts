import type { PublicUser } from "@/types/user"

// mirrors the backend NotificationType enum
export type NotificationType =
  | "DIARISTA_BUMPED"
  | "SESSION_CANCELED"
  | "ATTENDANCE_REMOVED_BY_ADMIN"
  | "ATTENDANCE_CONFIRMED_BY_ADMIN"
  | "SESSION_REMINDER"
  | "SESSION_CREATED"
  | "DIARISTA_WINDOW_OPEN"
  | "TEAMS_SORTED"
  | "SESSION_FINISHED"
  | "ADDED_TO_PELADA"
  | "REMOVED_FROM_PELADA"
  | "ADMIN_GRANTED"
  | "ADMIN_REVOKED"
  | "MENSALISTA_GRANTED"
  | "MENSALISTA_REVOKED"
  | "PELADA_TRANSFERRED"
  | "AWARD_WON"

// mirrors NotificationCategory (drives the e-mail preference)
export type NotificationCategory = "SESSION" | "ATTENDANCE" | "MEMBERSHIP" | "AWARDS"

// mirrors NotificationAward
export type NotificationAward = "PUSKAS" | "WILTBALL" | "ARTILHEIRO" | "GARCOM"

export const notificationCategoryLabel: Record<NotificationCategory, string> = {
  ATTENDANCE: "Presença",
  SESSION: "Sessões",
  MEMBERSHIP: "Membros",
  AWARDS: "Prêmios",
}

// same mapping as NotificationType.category() in the backend
export const notificationTypeCategory: Record<NotificationType, NotificationCategory> = {
  DIARISTA_BUMPED: "ATTENDANCE",
  SESSION_CANCELED: "ATTENDANCE",
  ATTENDANCE_REMOVED_BY_ADMIN: "ATTENDANCE",
  ATTENDANCE_CONFIRMED_BY_ADMIN: "ATTENDANCE",
  SESSION_REMINDER: "ATTENDANCE",
  SESSION_CREATED: "SESSION",
  DIARISTA_WINDOW_OPEN: "SESSION",
  TEAMS_SORTED: "SESSION",
  SESSION_FINISHED: "SESSION",
  ADDED_TO_PELADA: "MEMBERSHIP",
  REMOVED_FROM_PELADA: "MEMBERSHIP",
  ADMIN_GRANTED: "MEMBERSHIP",
  ADMIN_REVOKED: "MEMBERSHIP",
  MENSALISTA_GRANTED: "MEMBERSHIP",
  MENSALISTA_REVOKED: "MEMBERSHIP",
  PELADA_TRANSFERRED: "MEMBERSHIP",
  AWARD_WON: "AWARDS",
}

// mirrors NotificationDTO: references only, the text is rendered by utils/notifications
export interface NotificationResponse {
  id: number
  type: NotificationType
  category: NotificationCategory
  award: NotificationAward | null
  // ISO instants; readAt null = unread
  createdAt: string
  readAt: string | null
  pelada: { id: number; name: string; image: string | null } | null
  // date "yyyy-MM-dd", time "HH:mm"
  daily: { id: number; date: string; time: string } | null
  actor: PublicUser | null
  // only on TEAMS_SORTED: the recipient's team in that session
  team: { name: string; color: string | null } | null
}

// mirrors NotificationPushDTO: a frame on /user/queue/notifications, with the recipient's unread count after it
export interface NotificationPush {
  notification: NotificationResponse
  unreadCount: number
}

export interface UnreadCountResponse {
  count: number
}
