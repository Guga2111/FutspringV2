import { useMemo, useState } from "react"
import { Plus, Search } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { MemberCard, type MemberStats } from "@/components/pelada/MemberCard"
import { countByPosition, filterMembers, POSITION_FILTERS, type PositionFilter } from "@/utils/memberFilters"
import type { PeladaMember } from "@/types/pelada"
import type { RankingDTO } from "@/types/daily"

interface MembersGridProps {
  members: PeladaMember[]
  // the ranking the page already loads: games, goals and assists per member
  ranking: RankingDTO[]
  creatorId: number | null
  isCurrentUserAdmin: boolean
  togglingAdmin: number | null
  onAddPlayer: () => void
  onToggleAdmin: (member: PeladaMember) => void
  onRemoveMember: (member: PeladaMember) => void
}

const NO_STATS: MemberStats = { matchesPlayed: 0, goals: 0, assists: 0 }

export function MembersGrid({
  members,
  ranking,
  creatorId,
  isCurrentUserAdmin,
  togglingAdmin,
  onAddPlayer,
  onToggleAdmin,
  onRemoveMember,
}: MembersGridProps) {
  const [query, setQuery] = useState("")
  const [position, setPosition] = useState<PositionFilter>("ALL")

  const counts = useMemo(() => countByPosition(members), [members])
  const visible = useMemo(() => filterMembers(members, query, position), [members, query, position])
  const statsById = useMemo(() => new Map(ranking.map((r) => [r.userId, r])), [ranking])
  const adminCount = useMemo(() => members.filter((m) => m.isAdmin).length, [members])

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-[200px] max-w-[320px] flex-1">
          <Search aria-hidden className="pointer-events-none absolute left-3 top-1/2 size-[15px] -translate-y-1/2 text-subtle-foreground" />
          <Input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Buscar membro"
            aria-label="Buscar membro"
            className="h-[38px] rounded-full bg-card pl-[34px] pr-3.5"
          />
        </div>
        <ToggleGroup
          type="single"
          variant="chip"
          size="chip"
          value={position}
          onValueChange={(value) => value && setPosition(value as PositionFilter)}
          aria-label="Filtrar por posição"
          className="flex-wrap justify-start gap-1.5"
        >
          {POSITION_FILTERS.map((f) => (
            <ToggleGroupItem key={f.value} value={f.value} className="group">
              {f.label}
              <span className="text-xs text-subtle-foreground group-data-[state=on]:text-primary-foreground/60">{counts[f.value]}</span>
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
        {isCurrentUserAdmin && (
          <Button variant="gradient" onClick={onAddPlayer} className="ml-auto hidden h-9 gap-1.5 px-4 font-semibold md:inline-flex">
            <Plus className="size-[15px]" strokeWidth={2.2} />
            Adicionar jogador
          </Button>
        )}
      </div>

      <div className="flex min-h-10 items-center justify-between gap-3">
        <span className="text-[13px] text-subtle-foreground">
          {visible.length} de {members.length} membros · {adminCount} {adminCount === 1 ? "admin" : "admins"}
        </span>
        {isCurrentUserAdmin && (
          <Button variant="gradient" size="icon" aria-label="Adicionar jogador" onClick={onAddPlayer} className="size-10 shrink-0 md:hidden">
            <Plus className="size-[18px]" strokeWidth={2.4} />
          </Button>
        )}
      </div>

      {visible.length === 0 ? (
        <div className="rounded-xl border border-dashed border-input p-10 text-center text-sm text-subtle-foreground">
          Nenhum membro encontrado.
        </div>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(230px,1fr))] gap-3">
          {visible.map((member) => (
            <MemberCard
              key={member.id}
              member={member}
              stats={statsById.get(member.id) ?? NO_STATS}
              canManage={isCurrentUserAdmin && member.id !== creatorId}
              isToggling={togglingAdmin === member.id}
              onToggleAdmin={() => onToggleAdmin(member)}
              onRemove={() => onRemoveMember(member)}
            />
          ))}
        </div>
      )}
    </div>
  )
}
