import type { LandingPhoto } from "@/components/landing/content"
import { cn } from "@/lib/utils"

interface PhotoSlotProps {
  photo: LandingPhoto
  className?: string
  // the flat field shown while the owner's photo is missing
  standClassName?: string
  width: number
  height: number
  eager?: boolean
}

// One of the owner's photos, or a flat field with a short caption until the file exists
// (see LandingPhoto in content.ts)
export function PhotoSlot({ photo, className, standClassName, width, height, eager = false }: PhotoSlotProps) {
  if (photo.src) {
    return (
      <img
        src={photo.src}
        alt={photo.alt}
        width={width}
        height={height}
        loading={eager ? "eager" : "lazy"}
        className={cn("block h-full w-full object-cover", className)}
      />
    )
  }
  return (
    <div
      role="img"
      aria-label={photo.alt}
      className={cn("flex h-full w-full items-end bg-night p-5 text-night-foreground", className, standClassName)}
    >
      <span aria-hidden className="text-base font-semibold">
        {photo.stand}
      </span>
    </div>
  )
}
