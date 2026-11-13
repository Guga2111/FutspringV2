import apiClient from "./client"

export type ClientErrorSource = "WINDOW" | "PROMISE" | "BOUNDARY" | "CSP"

// mirrors ClientErrorRequestDTO (message ≤ 500, stack ≤ 4000, path ≤ 200, release ≤ 40, userAgent ≤ 300)
export interface ClientErrorReport {
  message: string
  stack: string | null
  path: string
  release: string
  userAgent: string
  source: ClientErrorSource
}

// Public route: an expired session still reports (the API ignores the Authorization header there)
export async function sendClientError(report: ClientErrorReport): Promise<void> {
  await apiClient.post("/api/v1/client-errors", report)
}
