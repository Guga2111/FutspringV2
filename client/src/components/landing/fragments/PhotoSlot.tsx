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
  // contain: the whole photo in the middle, its sides filled with a blurred, enlarged copy
  // of itself (portrait phone photos in a landscape card, nothing cropped or invented)
  fit?: "cover" | "contain"
}

// One of the owner's photos, or a flat field with a short caption until the file exists
// (see LandingPhoto in content.ts)
export function PhotoSlot({ photo, className, standClassName, width, height, eager = false, fit = "cover" }: PhotoSlotProps) {
  const loading = eager ? "eager" : "lazy"
  if (photo.src && fit === "contain") {
    return (
      <div className={cn("relative h-full w-full overflow-hidden bg-night", className)}>
        <img
          src={photo.src}
          alt=""
          aria-hidden
          width={width}
          height={height}
          loading={loading}
          className="absolute inset-0 h-full w-full scale-125 object-cover opacity-70 blur-2xl"
        />
        <img
          src={photo.src}
          alt={photo.alt}
          width={width}
          height={height}
          loading={loading}
          className="relative block h-full w-full object-contain"
        />
      </div>
    )
  }
  if (photo.src) {
    return (
      <img
        src={photo.src}
        alt={photo.alt}
        width={width}
        height={height}
        loading={loading}
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
