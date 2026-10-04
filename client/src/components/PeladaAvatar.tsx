import { cn, getFileUrl, getInitials, getPeladaGradient } from "@/lib/utils"

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
  if (image) {
    return (
      <img
        src={getFileUrl(image)}
        alt=""
        loading="lazy"
        className={cn("shrink-0 object-cover", className)}
        style={{ objectPosition }}
      />
    )
  }
  return (
    <div
      aria-hidden
      className={cn(
        "flex shrink-0 select-none items-center justify-center font-bold text-white",
        getPeladaGradient(name),
        className,
      )}
    >
      {getInitials(name)}
    </div>
  )
}
