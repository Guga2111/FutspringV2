import apiClient from "./client"

export async function getImage(filename: string, signal?: AbortSignal): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/api/v1/files/${encodeURIComponent(filename)}`, { responseType: "blob", ...(signal ? { signal } : {}) })
  return response.data
}
