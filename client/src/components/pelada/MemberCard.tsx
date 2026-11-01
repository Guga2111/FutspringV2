import { Link, useNavigate } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { CalendarCheckIn01Icon, CalendarRemove01Icon, CrownIcon, Delete02Icon, MoreHorizontalIcon, ShieldCheckIcon, ShieldOffIcon, StarIcon, UserIcon } from "@hugeicons/core-free-icons"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { MembershipBadge } from "@/components/MembershipBadge"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { StarRow } from "@/components/StarRow"
import { cn } from "@/lib/utils"
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
  // admin/remove actions (not on the creator's card)
  canManage: boolean
  // any admin sets any member's stars, the creator's and their own included
  canSetStars: boolean
  isToggling: boolean
  // dimmed and inert while a removal or a bulk request on this member runs
  isRemoving: boolean
  // "Limite atingido (8/8)" when no more mensalistas fit; null when this member can be made one
  mensalistaLimitHint: string | null
  onToggleAdmin: () => void
  onRemove: () => void
  onSetStars: () => void
  onToggleMensalista: () => void
  // selection mode (bulk actions): checkbox, the whole card toggles it and the ⋯ menu is hidden
  selectable?: boolean
  selected?: boolean
  onToggleSelected?: () => void
}

export function MemberCard({
  member,
  stats,
  canManage,
  canSetStars,
  isToggling,
  isRemoving,
  mensalistaLimitHint,
  onToggleAdmin,
  onRemove,
  onSetStars,
  onToggleMensalista,
  selectable = false,
  selected = false,
  onToggleSelected,
}: MemberCardProps) {
  const navigate = useNavigate()
  const position = positionShortLabel(member.position)

  return (
    <article
      aria-busy={isRemoving}
      onClick={selectable ? onToggleSelected : undefined}
      className={cn(
        "flex flex-col gap-3 rounded-xl border bg-card p-3.5 transition-[background-color,border-color,opacity] hover:border-input hover:bg-card-elevated",
        selectable && "cursor-pointer select-none",
        selected && "border-primary ring-1 ring-primary hover:border-primary",
        isRemoving && "pointer-events-none opacity-50",
      )}
    >
      <div className="flex items-center gap-3">
        {selectable && (
          <Checkbox
            checked={selected}
            onCheckedChange={onToggleSelected}
            // the card's own click would toggle it a second time
            onClick={(e) => e.stopPropagation()}
            aria-label={`Selecionar ${member.username}`}
            className="shrink-0"
          />
        )}
        <PlayerAvatar username={member.username} image={member.image} colorId={member.id} className="size-[42px] text-[13px]" />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex min-w-0 items-center gap-1.5">
            {selectable ? (
              <span className="truncate text-[15px] font-semibold">{member.username}</span>
            ) : (
              <Link to={`/profile/${member.id}`} className="truncate text-[15px] font-semibold hover:underline">
                {member.username}
              </Link>
            )}
            {member.isAdmin && <HugeiconsIcon icon={CrownIcon} aria-label="Admin" className="size-3.5 shrink-0 text-gold" />}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <MembershipBadge isMensalista={member.isMensalista} />
            {position && (
              <Badge variant="status" className="bg-secondary px-2 py-0.5 text-[11px] font-semibold uppercase tracking-[0.03em] text-secondary-foreground">
                {position}
              </Badge>
            )}
            <StarRow stars={member.stars} />
          </div>
        </div>
        {!selectable && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Ações de ${member.username}`}
                disabled={isToggling || isRemoving}
                className="size-7 shrink-0 self-start text-subtle-foreground hover:bg-secondary hover:text-foreground"
              >
                <HugeiconsIcon icon={MoreHorizontalIcon} className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-52 rounded-[10px] shadow-menu">
              <DropdownMenuItem onSelect={() => navigate(`/profile/${member.id}`)}>
                <HugeiconsIcon icon={UserIcon} className="mr-2 size-4 text-muted-foreground" />
                Ver perfil
              </DropdownMenuItem>
              {canSetStars && (
                <DropdownMenuItem onSelect={onSetStars}>
                  <HugeiconsIcon icon={StarIcon} className="mr-2 size-4 text-muted-foreground" />
                  Definir estrelas
                </DropdownMenuItem>
              )}
              {canSetStars && (
                <DropdownMenuItem
                  onSelect={onToggleMensalista}
                  disabled={isToggling || (!member.isMensalista && mensalistaLimitHint !== null)}
                >
                  {member.isMensalista ? (
                    <HugeiconsIcon icon={CalendarRemove01Icon} className="mr-2 size-4 text-muted-foreground" />
                  ) : (
                    <HugeiconsIcon icon={CalendarCheckIn01Icon} className="mr-2 size-4 text-muted-foreground" />
                  )}
                  <span className="flex flex-col">
                    {member.isMensalista ? "Tornar diarista" : "Tornar mensalista"}
                    {!member.isMensalista && mensalistaLimitHint && (
                      <span className="text-xs text-muted-foreground">{mensalistaLimitHint}</span>
                    )}
                  </span>
                </DropdownMenuItem>
              )}
              {canManage && (
                <>
                  <DropdownMenuItem onSelect={onToggleAdmin} disabled={isToggling}>
                    {member.isAdmin ? (
                      <HugeiconsIcon icon={ShieldOffIcon} className="mr-2 size-4 text-muted-foreground" />
                    ) : (
                      <HugeiconsIcon icon={ShieldCheckIcon} className="mr-2 size-4 text-muted-foreground" />
                    )}
                    {member.isAdmin ? "Remover admin" : "Tornar admin"}
                  </DropdownMenuItem>
                  <DropdownMenuSeparator className="bg-border" />
                  <DropdownMenuItem onSelect={onRemove} className="text-destructive focus:bg-destructive-muted focus:text-destructive">
                    <HugeiconsIcon icon={Delete02Icon} className="mr-2 size-4" />
                    Remover
                  </DropdownMenuItem>
                </>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        )}
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
