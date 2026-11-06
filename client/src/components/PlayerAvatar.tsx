import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { cn, getAvatarColor, getFileUrl, getInitials } from "@/lib/utils"

interface PlayerAvatarProps {
  // the player's account id: the initials take its color, the same on every screen.
  // null only for people without an account (finance guests), who get the neutral avatar
  userId: number | null
  username: string
  image: string | null
  // size and initials font size come from the caller (e.g. "size-7 text-[11px]")
  className?: string
  // a deleted account ("Ex-jogador"): the neutral avatar, like a guest
  deleted?: boolean
}

// Round player avatar: photo, or initials on the player's own color
export function PlayerAvatar({ userId, username, image, className, deleted = false }: PlayerAvatarProps) {
  return (
    <Avatar className={cn("size-7 shrink-0 text-[11px]", className)}>
      {image && <AvatarImage src={getFileUrl(image)} alt="" loading="lazy" className="object-cover" />}
      <AvatarFallback
        className={userId != null && !deleted ? cn(getAvatarColor(userId), "font-bold text-white") : "bg-avatar-fallback font-semibold"}
      >
        {getInitials(username)}
      </AvatarFallback>
    </Avatar>
  )
}
