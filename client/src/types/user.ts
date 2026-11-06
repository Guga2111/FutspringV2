// mirrors ProfileDTO; email is only sent to the profile owner
export interface ProfileDTO {
  id: number
  username: string
  email?: string
  image: string | null
  backgroundImage: string | null
  // rounded average of the user's stars in their peladas; absent when they belong to none
  stars?: number
  position: string | null
}

// mirrors PublicUserDTO (user search): no email
export interface PublicUser {
  id: number
  username: string
  image: string | null
  position: string | null
  // a deleted account ("Ex-jogador"): neutral avatar, no profile link
  isDeleted: boolean
}

// UpdateProfileRequest accepts these values (English and Portuguese, kept for older profiles)
export type Position =
  | "GOALKEEPER" | "DEFENDER" | "MIDFIELDER" | "FORWARD"
  | "GOLEIRO" | "ZAGUEIRO" | "MEIO" | "ATACANTE"

export const positionLabel: Record<Position, string> = {
  GOALKEEPER: "Goleiro",
  DEFENDER: "Zagueiro",
  MIDFIELDER: "Meio-campo",
  FORWARD: "Atacante",
  GOLEIRO: "Goleiro",
  ZAGUEIRO: "Zagueiro",
  MEIO: "Meio-campo",
  ATACANTE: "Atacante",
}

// The positions offered when editing a profile
export const EDITABLE_POSITIONS = ["GOLEIRO", "ZAGUEIRO", "MEIO", "ATACANTE"] as const satisfies readonly Position[]

export function getPositionLabel(position: string | null | undefined): string | null {
  if (!position) return null
  return positionLabel[position as Position] ?? position
}

// mirrors DeletionBlockerDTO: what blocks deleting the account, one per pelada
export type DeletionBlockerType = "CREATOR" | "SOLE_FINANCE_MANAGER"

export interface DeletionBlocker {
  type: DeletionBlockerType
  peladaId: number
  peladaName: string
}
