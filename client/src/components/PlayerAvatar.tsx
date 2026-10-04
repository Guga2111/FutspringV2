import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { cn, getAvatarColor, getFileUrl, getInitials } from "@/lib/utils"

interface PlayerAvatarProps {
  username: string
  image: string | null
  // size and initials font size come from the caller (e.g. "size-7 text-[11px]")
  className?: string
  // colored initials by player id (members grid); neutral when omitted
  colorId?: number
}

// Round player avatar: photo, or initials on a neutral / per-player color
export function PlayerAvatar({ username, image, className, colorId }: PlayerAvatarProps) {
  return (
    <Avatar className={cn("size-7 shrink-0 text-[11px]", className)}>
      {image && <AvatarImage src={getFileUrl(image)} alt="" loading="lazy" className="object-cover" />}
      <AvatarFallback
        className={cn(
          "font-semibold",
          colorId != null ? cn(getAvatarColor(colorId), "font-bold text-white") : "bg-avatar-fallback",
        )}
      >
        {getInitials(username)}
      </AvatarFallback>
    </Avatar>
  )
}
