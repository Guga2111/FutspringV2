import { useImageUrl } from "@/hooks/useImageUrl"
import { cn, getInitials, getPeladaColor } from "@/lib/utils"

interface PeladaAvatarProps {
  name: string
  image: string | null
  // size, radius and initials font come from the caller
  className?: string
  // vertical focus of the photo (the design crops pelada photos near the top)
  objectPosition?: string
}

// Pelada photo, or its gradient with initials when there is none
export function PeladaAvatar({ name, image, className, objectPosition = "center 18%" }: PeladaAvatarProps) {
  const { url: photo, ref } = useImageUrl(image)
  if (photo) {
    return (
      <img
        ref={ref}
        src={photo}
        alt=""
        loading="lazy"
        className={cn("shrink-0 object-cover", className)}
        style={{ objectPosition }}
      />
    )
  }
  return (
    <div
      ref={ref}
      aria-hidden
      className={cn(
        "flex shrink-0 select-none items-center justify-center font-bold text-white",
        getPeladaColor(name),
        className,
      )}
    >
      {getInitials(name)}
    </div>
  )
}
