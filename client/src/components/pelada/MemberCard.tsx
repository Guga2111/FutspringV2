import { Link, useNavigate } from "react-router-dom"
import { Crown, MoreHorizontal, ShieldCheck, ShieldOff, Trash2, User } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { StarRow } from "@/components/StarRow"
import { positionShortLabel } from "@/utils/memberFilters"
import type { PeladaMember } from "@/types/pelada"

export interface MemberStats {
  matchesPlayed: number
  goals: number
  assists: number
}

interface MemberCardProps {
  member: PeladaMember
  stats: MemberStats
  canManage: boolean
  isToggling: boolean
  onToggleAdmin: () => void
  onRemove: () => void
}

export function MemberCard({ member, stats, canManage, isToggling, onToggleAdmin, onRemove }: MemberCardProps) {
  const navigate = useNavigate()
  const position = positionShortLabel(member.position)

  return (
    <article className="flex flex-col gap-3 rounded-xl border bg-card p-3.5 transition-colors hover:border-input hover:bg-card-elevated">
      <div className="flex items-center gap-3">
        <PlayerAvatar username={member.username} image={member.image} colorId={member.id} className="size-[42px] text-[13px]" />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex min-w-0 items-center gap-1.5">
            <Link to={`/profile/${member.id}`} className="truncate text-[15px] font-semibold hover:underline">
              {member.username}
            </Link>
            {member.isAdmin && <Crown aria-label="Admin" className="size-3.5 shrink-0 text-gold" />}
          </div>
          <div className="flex items-center gap-2">
            {position && (
              <Badge variant="status" className="bg-secondary px-2 py-0.5 text-[11px] font-semibold uppercase tracking-[0.03em] text-secondary-foreground">
                {position}
              </Badge>
            )}
            <StarRow stars={member.stars} />
          </div>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Ações de ${member.username}`}
              disabled={isToggling}
              className="size-7 shrink-0 self-start text-subtle-foreground hover:bg-secondary hover:text-foreground"
            >
              <MoreHorizontal className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-52 rounded-[10px] shadow-menu">
            <DropdownMenuItem onSelect={() => navigate(`/profile/${member.id}`)}>
              <User className="mr-2 size-4 text-muted-foreground" />
              Ver perfil
            </DropdownMenuItem>
            {canManage && (
              <>
                <DropdownMenuItem onSelect={onToggleAdmin} disabled={isToggling}>
                  {member.isAdmin ? (
                    <ShieldOff className="mr-2 size-4 text-muted-foreground" />
                  ) : (
                    <ShieldCheck className="mr-2 size-4 text-muted-foreground" />
                  )}
                  {member.isAdmin ? "Remover admin" : "Tornar admin"}
                </DropdownMenuItem>
                <DropdownMenuSeparator className="bg-border" />
                <DropdownMenuItem onSelect={onRemove} className="text-destructive focus:bg-destructive-muted focus:text-destructive">
                  <Trash2 className="mr-2 size-4" />
                  Remover
                </DropdownMenuItem>
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <dl className="grid grid-cols-3 border-t pt-2.5">
        {(
          [
            ["Jogos", stats.matchesPlayed],
            ["Gols", stats.goals],
            ["Assist.", stats.assists],
          ] as const
        ).map(([label, value]) => (
          <div key={label} className="flex flex-col-reverse gap-0.5">
            <dt className="text-[11px] text-subtle-foreground">{label}</dt>
            <dd className="text-base font-semibold tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
    </article>
  )
}
