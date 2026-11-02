import { StarRow } from "@/components/StarRow"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { getFileUrl, getInitials } from "@/lib/utils"
import { getPositionLabel, type ProfileDTO } from "@/types/user"

interface ProfileHeaderProps {
  profile: ProfileDTO
  isOwnProfile: boolean
  // null while the peladas are loading
  peladaCount: number | null
  onEdit: () => void
}

// The avatar, the name and one line of facts
export function ProfileHeader({ profile, isOwnProfile, peladaCount, onEdit }: ProfileHeaderProps) {
  const position = getPositionLabel(profile.position)

  return (
    <header className="flex flex-col">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:gap-5">
        <Avatar className="size-20 md:size-24">
          <AvatarImage src={getFileUrl(profile.image)} alt={profile.username} className="object-cover" />
          <AvatarFallback className="bg-avatar-fallback font-display text-heading font-bold md:text-heading-lg">
            {getInitials(profile.username)}
          </AvatarFallback>
        </Avatar>

        <div className="flex min-w-0 flex-1 flex-col gap-1.5 md:pb-1">
          <h1 className="truncate font-display text-heading-lg font-bold md:text-display">{profile.username}</h1>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-body-sm text-muted-foreground">
            {position && <Badge variant="secondary">{position}</Badge>}
            {profile.stars != null ? (
              <span title="Média das estrelas nas peladas">
                <StarRow stars={profile.stars} className="text-body" />
              </span>
            ) : (
              <span>Sem avaliação</span>
            )}
            {peladaCount != null && (
              <span>
                {peladaCount === 1 ? "1 pelada" : `${peladaCount} peladas`}
                {!isOwnProfile && " em comum"}
              </span>
            )}
          </div>
        </div>

        {isOwnProfile && (
          <Button variant="outline" onClick={onEdit} className="w-full md:mb-1 md:w-auto">
            Editar perfil
          </Button>
        )}
      </div>
    </header>
  )
}
