import { useMemo, useState } from "react"
import { ListChecks, Plus, Search, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { MemberCard, type MemberStats } from "@/components/pelada/MemberCard"
import { MemberSelectionBar } from "@/components/pelada/MemberSelectionBar"
import { useMemberSelection } from "@/components/pelada/hooks/useMemberSelection"
import type { BulkMemberAction } from "@/utils/bulkMemberActions"
import {
  countByMembership,
  countByPosition,
  filterMembers,
  MEMBERSHIP_FILTERS,
  POSITION_FILTERS,
  type MembershipFilter,
  type PositionFilter,
} from "@/utils/memberFilters"
import type { PeladaMember } from "@/types/pelada"
import type { RankingDTO } from "@/types/daily"

interface MembersGridProps {
  members: PeladaMember[]
  // the ranking the page already loads: games, goals and assists per member
  ranking: RankingDTO[]
  creatorId: number | null
  currentUserId: number | null
  maxMensalistas: number
  isCurrentUserAdmin: boolean
  togglingAdmin: number | null
  togglingMensalista: number | null
  settingStars: number | null
  removingMember: number | null
  // members a bulk request is changing
  bulkPending: ReadonlySet<number> | null
  onAddPlayer: () => void
  onToggleAdmin: (member: PeladaMember) => void
  onRemoveMember: (member: PeladaMember) => void
  onSetStars: (member: PeladaMember) => void
  onSetMensalista: (member: PeladaMember, isMensalista: boolean) => void
  // resolves true on success, which ends selection mode
  onBulkAction: (action: BulkMemberAction, targets: PeladaMember[], stars?: number) => Promise<boolean>
}

const NO_STATS: MemberStats = { matchesPlayed: 0, goals: 0, assists: 0 }

export function MembersGrid({
  members,
  ranking,
  creatorId,
  currentUserId,
  maxMensalistas,
  isCurrentUserAdmin,
  togglingAdmin,
  togglingMensalista,
  settingStars,
  removingMember,
  bulkPending,
  onAddPlayer,
  onToggleAdmin,
  onRemoveMember,
  onSetStars,
  onSetMensalista,
  onBulkAction,
}: MembersGridProps) {
  const [query, setQuery] = useState("")
  const [position, setPosition] = useState<PositionFilter>("ALL")
  const [membership, setMembership] = useState<MembershipFilter>("ALL")

  const counts = useMemo(() => countByPosition(members), [members])
  const membershipCounts = useMemo(() => countByMembership(members), [members])
  const visible = useMemo(
    () => filterMembers(members, query, position, membership),
    [members, query, position, membership],
  )
  const limitHint =
    membershipCounts.MENSALISTA >= maxMensalistas
      ? `Limite atingido (${membershipCounts.MENSALISTA}/${maxMensalistas})`
      : null
  const statsById = useMemo(() => new Map(ranking.map((r) => [r.userId, r])), [ranking])
  const adminCount = useMemo(() => members.filter((m) => m.isAdmin).length, [members])
  const selection = useMemberSelection(members, visible)
  const selecting = isCurrentUserAdmin && selection.active

  async function handleBulkAction(action: BulkMemberAction, targets: PeladaMember[], stars?: number) {
    if (await onBulkAction(action, targets, stars)) selection.exit()
  }

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
        {/* deselecting goes back to every member */}
        <ToggleGroup
          type="single"
          variant="chip"
          size="chip"
          value={membership === "ALL" ? "" : membership}
          onValueChange={(value) => setMembership((value || "ALL") as MembershipFilter)}
          aria-label="Filtrar por tipo"
          className="flex-wrap justify-start gap-1.5"
        >
          {MEMBERSHIP_FILTERS.map((f) => (
            <ToggleGroupItem key={f.value} value={f.value} className="group">
              {f.label}
              <span className="text-xs text-subtle-foreground group-data-[state=on]:text-primary-foreground/60">
                {membershipCounts[f.value]}
              </span>
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
        {isCurrentUserAdmin && (
          <Button
            variant="outline"
            onClick={selecting ? selection.exit : selection.enter}
            aria-pressed={selecting}
            className="h-9 gap-1.5 px-4 md:ml-auto"
          >
            {selecting ? <X className="size-[15px]" /> : <ListChecks className="size-[15px]" />}
            {selecting ? "Cancelar" : "Selecionar"}
          </Button>
        )}
        {isCurrentUserAdmin && (
          <Button variant="gradient" onClick={onAddPlayer} className="hidden h-9 gap-1.5 px-4 font-semibold md:inline-flex">
            <Plus className="size-[15px]" strokeWidth={2.2} />
            Adicionar jogador
          </Button>
        )}
      </div>

      <div className="flex min-h-10 items-center justify-between gap-3">
        {selecting && selection.selected.length === 0 ? (
          <span className="flex flex-wrap items-center gap-x-2 text-[13px] text-subtle-foreground">
            Toque nos membros para selecionar
            <Button variant="link" onClick={selection.toggleVisible} className="h-auto p-0 text-[13px]">
              Selecionar visíveis
            </Button>
          </span>
        ) : (
          <span className="text-[13px] text-subtle-foreground">
            {visible.length} de {members.length} membros · {adminCount} {adminCount === 1 ? "admin" : "admins"}
          </span>
        )}
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
              canSetStars={isCurrentUserAdmin}
              isToggling={
                togglingAdmin === member.id || togglingMensalista === member.id || settingStars === member.id
              }
              isRemoving={removingMember === member.id || (bulkPending?.has(member.id) ?? false)}
              mensalistaLimitHint={limitHint}
              onToggleAdmin={() => onToggleAdmin(member)}
              onRemove={() => onRemoveMember(member)}
              onSetStars={() => onSetStars(member)}
              onToggleMensalista={() => onSetMensalista(member, !member.isMensalista)}
              selectable={selecting}
              selected={selection.selectedIds.has(member.id)}
              onToggleSelected={() => selection.toggle(member.id)}
            />
          ))}
        </div>
      )}

      {selecting && selection.selected.length > 0 && (
        <MemberSelectionBar
          selected={selection.selected}
          outsideFilter={selection.outsideFilter}
          allVisibleSelected={selection.allVisibleSelected}
          pending={bulkPending !== null}
          creatorId={creatorId}
          currentUserId={currentUserId}
          mensalistaCount={membershipCounts.MENSALISTA}
          maxMensalistas={maxMensalistas}
          onToggleVisible={selection.toggleVisible}
          onExit={selection.exit}
          onAction={(action, targets, stars) => void handleBulkAction(action, targets, stars)}
        />
      )}
    </div>
  )
}
