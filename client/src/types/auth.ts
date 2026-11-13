export interface UserResponseDTO {
  id: number
  email: string
  username: string
  image: string | null
  backgroundImage: string | null
  position: string | null
  // operator of the platform (the admin panel); absent in a session stored before it existed
  isPlatformAdmin?: boolean
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

export interface VerifyEmailRequest {
  token: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}
