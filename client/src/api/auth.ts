import apiClient from "./client"
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/types/auth"

export async function registerUser(data: RegisterRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/v1/auth/register", data)
  return response.data
}

export async function loginUser(data: LoginRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/v1/auth/login", data)
  return response.data
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
