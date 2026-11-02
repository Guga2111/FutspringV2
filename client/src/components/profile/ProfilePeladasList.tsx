import { Link } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowRight01Icon } from "@hugeicons/core-free-icons"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { dayOfWeekLabel } from "@/lib/constants"
import type { PeladaResponse } from "@/types/pelada"

interface ProfilePeladasListProps {
  peladas: PeladaResponse[]
  loading: boolean
  isOwnProfile: boolean
}

// The user's peladas (or the ones the viewer shares with them) as link rows
export function ProfilePeladasList({ peladas, loading, isOwnProfile }: ProfilePeladasListProps) {
  return (
    <Card>
      <CardHeader className="p-4 pb-2 md:p-5 md:pb-2">
        <CardTitle className="text-title font-semibold">{isOwnProfile ? "Peladas" : "Peladas em comum"}</CardTitle>
      </CardHeader>
      <CardContent className="p-2 pt-0 md:p-3 md:pt-0">
        {loading ? (
          <div className="grid gap-1 md:grid-cols-2">
            {[0, 1, 2].map((i) => (
              <div key={i} className="flex items-center gap-3 p-2">
                <Skeleton className="size-11 rounded-tile" />
                <div className="flex flex-1 flex-col gap-1.5">
                  <Skeleton className="h-4 w-2/3" />
                  <Skeleton className="h-3 w-1/2" />
                </div>
              </div>
            ))}
          </div>
        ) : peladas.length === 0 ? (
          <p className="px-2 pb-2 text-body text-muted-foreground md:px-2">
            {isOwnProfile ? "Você ainda não está em nenhuma pelada." : "Vocês não têm peladas em comum."}
          </p>
        ) : (
          <ul className="grid gap-1 md:grid-cols-2">
            {peladas.map((pelada) => (
              <li key={pelada.id}>
                <Link
                  to={`/pelada/${pelada.id}`}
                  className="group flex items-center gap-3 rounded-tile p-2 transition-colors hover:bg-row-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <PeladaAvatar name={pelada.name} image={pelada.image} className="size-11 rounded-tile text-body" />
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="flex min-w-0 items-center gap-2">
                      <span className="truncate text-body font-semibold">{pelada.name}</span>
                      {/* isAdmin is the caller's role, so it only means something on the caller's own profile */}
                      {isOwnProfile && pelada.isAdmin && (
                        <Badge variant="status" className="bg-brand-muted text-brand-text">
                          Admin
                        </Badge>
                      )}
                    </span>
                    <span className="truncate text-body-sm text-muted-foreground">
                      {dayOfWeekLabel(pelada.dayOfWeek)} · {pelada.timeOfDay} ·{" "}
                      {pelada.memberCount === 1 ? "1 membro" : `${pelada.memberCount} membros`}
                    </span>
                  </div>
                  <HugeiconsIcon
                    icon={ArrowRight01Icon}
                    className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5"
                  />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
