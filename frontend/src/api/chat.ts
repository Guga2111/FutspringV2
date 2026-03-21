import apiClient from "./client"
import type { MessageDTO } from "../types/chat"

export async function getChatHistory(peladaId: number, page = 0, size = 50): Promise<MessageDTO[]> {
  const response = await apiClient.get<MessageDTO[]>(`/api/v1/peladas/${peladaId}/messages`, {
    params: { page, size },
  })
  return response.data
}
