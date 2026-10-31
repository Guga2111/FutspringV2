import type { PublicUser } from "@/types/user"

export type InvitationStatus = "PENDING" | "ACCEPTED" | "DECLINED" | "CANCELED" | "EXPIRED"
export const invitationStatusLabel: Record<InvitationStatus, string> = {
  PENDING: "Pendente", ACCEPTED: "Aceito", DECLINED: "Recusado", CANCELED: "Cancelado", EXPIRED: "Expirado",
}

// Public metadata only. The id is for routing; the preview displays name, image, day and time.
export interface PeladaPreview {
  id: number
  name: string
  image: string | null
  dayOfWeek: string
  timeOfDay: string
}
export interface Invitation {
  id: number
  peladaId: number
  pelada: PeladaPreview
  recipient: PublicUser
  sender: PublicUser
  status: InvitationStatus
  createdAt: string
  expiresAt: string
  respondedAt: string | null
  canceledAt: string | null
  isMember: boolean
}
export interface InviteLink {
  token: string
  createdAt: string
  expiresAt: string
  creator: PublicUser
}
export interface JoinPelada { peladaId: number }
