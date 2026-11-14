import apiClient from "./client"
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/types/auth"

// The routes that set or read the futspring_refresh cookie send credentials: in dev the API is another origin
// (5173 → 8080, same site), and without them the browser neither stores nor sends the cookie
export const WITH_SESSION_COOKIE = { withCredentials: true } as const

export async function registerUser(data: RegisterRequest): Promise<void> {
  await apiClient.post("/api/v1/auth/register", data)
}

export async function requestVerificationEmail(email: string): Promise<void> {
  await apiClient.post("/api/v1/auth/verification-email", { email })
}

export async function verifyEmail(data: import("@/types/auth").VerifyEmailRequest): Promise<void> {
  await apiClient.post("/api/v1/auth/verify-email", data)
}

export async function loginUser(data: LoginRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/v1/auth/login", data, WITH_SESSION_COOKIE)
  return response.data
}

// A new access token and a new refresh cookie from the cookie alone. 401: the session ended (cookie cleared);
// 503: the server can't renew now (Redis down), the session may still be valid
export async function refreshSession(signal?: AbortSignal): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/v1/auth/refresh", null, { ...WITH_SESSION_COOKIE, ...(signal ? { signal } : {}) })
  return response.data
}

// Ends this browser's session on the server and clears the cookie; idempotent
export async function logoutSession(): Promise<void> {
  await apiClient.post("/api/v1/auth/logout", null, WITH_SESSION_COOKIE)
}

// mirrors ResetPasswordRequestDTO
export interface ResetPasswordData {
  token: string
  newPassword: string
}

// 204 whether or not the e-mail has an account; the backend sends the link only when it does
export async function requestPasswordReset(email: string): Promise<void> {
  await apiClient.post("/api/v1/auth/forgot-password", { email })
}

// 400 when the link is unknown, used or expired. Ends every session opened before (the user logs in again)
export async function resetPassword(data: ResetPasswordData): Promise<void> {
  await apiClient.post("/api/v1/auth/reset-password", data)
}
