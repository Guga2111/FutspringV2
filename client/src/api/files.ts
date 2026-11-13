import apiClient from "./client"

export async function getImage(filename: string): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/api/v1/files/${encodeURIComponent(filename)}`, { responseType: "blob" })
  return response.data
}
