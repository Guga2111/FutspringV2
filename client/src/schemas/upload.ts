import { z } from "zod"

// mirrors FileUploadService: 5 MB, JPEG / PNG / WebP
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024
export const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"] as const
export const IMAGE_ACCEPT = IMAGE_TYPES.join(",")

export const imageFileSchema = z
  .instanceof(File)
  .refine((file) => file.size <= MAX_IMAGE_BYTES, "A imagem deve ter no máximo 5 MB")
  .refine((file) => (IMAGE_TYPES as readonly string[]).includes(file.type), "Somente imagens JPEG, PNG ou WebP")

// First validation message for a picked file, or null when it is acceptable
export function validateImageFile(file: File): string | null {
  const result = imageFileSchema.safeParse(file)
  return result.success ? null : (result.error.issues[0]?.message ?? "Imagem inválida")
}
