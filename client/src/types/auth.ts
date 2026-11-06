export interface UserResponseDTO {
  id: number
  email: string
  username: string
  image: string | null
  backgroundImage: string | null
  position: string | null
}

export interface AuthResponse {
  token: string
  user: UserResponseDTO
}

// mirrors RegisterRequestDTO; termsVersion is the LEGAL_VERSION the user accepted
export interface RegisterRequest {
  username: string
  email: string
  password: string
  termsVersion: string
}

export interface LoginRequest {
  email: string
  password: string
}
