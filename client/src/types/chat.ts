export interface SenderDTO {
  id: number
  username: string
  image: string | null
  // a deleted account: the message stays, signed "Ex-jogador"
  isDeleted: boolean
}

export interface MessageDTO {
  id: number
  content: string
  sentAt: string
  sender: SenderDTO
}
