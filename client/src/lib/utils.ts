import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"
import { API_BASE } from "@/lib/config"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}


export function getFileUrl(filename: string | null | undefined): string | undefined {
  if (!filename) return undefined
  return `${API_BASE}/api/v1/files/${filename}`
}

// "Pelada do Fut" -> "PF", "leal" -> "LE"; used for avatar and banner fallbacks
export function getInitials(name: string): string {
  const tokens = name.trim().split(/\s+/).filter(Boolean)
  if (tokens.length === 0) return '?'
  if (tokens.length === 1) return tokens[0].slice(0, 2).toUpperCase()
  return (tokens[0][0] + tokens[tokens.length - 1][0]).toUpperCase()
}

const AVATAR_COLORS = [
  "bg-avatar-1",
  "bg-avatar-2",
  "bg-avatar-3",
  "bg-avatar-4",
  "bg-avatar-5",
  "bg-avatar-6",
] as const

// Stable background for a player's initials avatar (tokens --avatar-1..6)
export function getAvatarColor(id: number): string {
  return AVATAR_COLORS[Math.abs(id) % AVATAR_COLORS.length]
}

// Stable background for a pelada without a photo, from its name (the same --avatar-1..6 tokens)
export function getPeladaColor(name: string): string {
  let hash = 5381
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 33) ^ name.charCodeAt(i)
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}
