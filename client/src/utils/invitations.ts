import { format, formatDistanceStrict } from "date-fns"
import { ptBR } from "date-fns/locale"
import type { Invitation } from "@/types/invitation"

// "03/11 às 15:00" in the viewer's zone
export function formatInvitationDate(iso: string): string {
  return format(new Date(iso), "dd/MM 'às' HH:mm", { locale: ptBR })
}

// "em 5 dias" / "há 2 horas", rounded the way people say it
export function relativeToNow(iso: string, now: Date): string {
  return formatDistanceStrict(new Date(iso), now, { locale: ptBR, addSuffix: true })
}

// The time column of an invitation row: when it expires (pending) or how it ended (history)
export function invitationTimeLabel(invitation: Invitation, now: Date): string {
  if (invitation.status === "PENDING") return `Expira ${relativeToNow(invitation.expiresAt, now)}`
  if (invitation.status === "EXPIRED") return `Expirou em ${formatInvitationDate(invitation.expiresAt)}`
  if (invitation.canceledAt) return `Cancelado em ${formatInvitationDate(invitation.canceledAt)}`
  if (invitation.respondedAt) return `Respondido em ${formatInvitationDate(invitation.respondedAt)}`
  return `Expira em ${formatInvitationDate(invitation.expiresAt)}`
}
