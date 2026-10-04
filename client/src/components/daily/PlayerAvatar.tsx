import type { PlayerDTO } from '../../types/daily'
import { getFileUrl } from '../../lib/utils'

interface PlayerAvatarProps {
  player: PlayerDTO
}

export default function PlayerAvatar({ player }: PlayerAvatarProps) {
  if (player.image) {
    return (
      <img
        src={getFileUrl(player.image)}
        alt={player.username}
        className="h-10 w-10 rounded-full object-cover flex-shrink-0"
      />
    )
  }
  const initials = player.username.slice(0, 2).toUpperCase()
  return (
    <div className="h-10 w-10 rounded-full bg-muted flex items-center justify-center flex-shrink-0 text-sm font-semibold">
      {initials}
    </div>
  )
}
