export interface UserResponseDTO {
  id: number
  email: string
  username: string
  image: string | null
  backgroundImage: string | null
  stars: number
  position: string | null
}

export interface AuthResponse {
  token: string
  user: UserResponseDTO
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}
