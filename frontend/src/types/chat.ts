export interface SenderDTO {
  id: number
  username: string
  image: string | null
}

export interface MessageDTO {
  id: number
  content: string
  sentAt: string
  sender: SenderDTO
}
