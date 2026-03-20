import apiClient from "./client"
import type { AuthResponse, LoginRequest, RegisterRequest } from "../types/auth"

export async function registerUser(data: RegisterRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/v1/auth/register", data)
  return response.data
}

export async function loginUser(data: LoginRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/v1/auth/login", data)
  return response.data
}
