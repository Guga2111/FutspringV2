// Error bodies sent by the API (GlobalExceptionHandler):
// validation 400 -> { status, message, timestamp, errors: { field: message } }; anything else -> { status, message, timestamp }
export interface ApiErrorBody {
  status?: number
  message?: string
  errors?: Record<string, string>
}

export function getApiErrorBody(error: unknown): ApiErrorBody | null {
  if (typeof error !== "object" || error === null || !("response" in error)) return null
  const data = (error as { response?: { data?: unknown } }).response?.data
  return data && typeof data === "object" ? (data as ApiErrorBody) : null
}

export function getErrorStatus(error: unknown): number | undefined {
  if (typeof error !== "object" || error === null || !("response" in error)) return undefined
  return (error as { response?: { status?: number } }).response?.status
}

// Message to show the user: the first field error of a validation 400, else the API message, else the fallback
export function getErrorMessage(error: unknown, fallback: string): string {
  const body = getApiErrorBody(error)
  const firstFieldError = body?.errors ? Object.values(body.errors)[0] : undefined
  return firstFieldError ?? body?.message ?? fallback
}
