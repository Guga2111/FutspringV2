import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

const API_BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080"

export function getFileUrl(filename: string | null | undefined): string | undefined {
  if (!filename) return undefined
  return `${API_BASE}/api/v1/files/${filename}`
}

const PELADA_GRADIENTS = [
  'bg-gradient-to-br from-blue-500 to-blue-700',
  'bg-gradient-to-br from-purple-500 to-purple-700',
  'bg-gradient-to-br from-rose-500 to-rose-700',
  'bg-gradient-to-br from-amber-500 to-amber-700',
  'bg-gradient-to-br from-cyan-500 to-cyan-700',
  'bg-gradient-to-br from-indigo-500 to-indigo-700',
  'bg-gradient-to-br from-pink-500 to-pink-700',
  'bg-gradient-to-br from-teal-500 to-teal-700',
]

export function getPeladaInitials(name: string): string {
  const tokens = name.trim().split(/\s+/).filter(Boolean)
  if (tokens.length === 0) return '?'
  if (tokens.length === 1) return tokens[0].slice(0, 2).toUpperCase()
  return (tokens[0][0] + tokens[tokens.length - 1][0]).toUpperCase()
}

export function getPeladaGradient(name: string): string {
  let hash = 5381
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 33) ^ name.charCodeAt(i)
  }
  return PELADA_GRADIENTS[Math.abs(hash) % PELADA_GRADIENTS.length]
}
