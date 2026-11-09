import { useRef, useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Alert01Icon, ArrowDataTransferHorizontalIcon, PencilEdit02Icon, ShuffleIcon, UserEdit01Icon } from "@hugeicons/core-free-icons"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { StarRow } from "@/components/StarRow"
import { TeamCard, TeamPlayerRow } from "@/components/daily/TeamCard"
import { TeamColorDot } from "@/components/daily/TeamColorDot"
import { cn } from "@/lib/utils"
import { canSortTeams, sortTeamsHint } from "@/utils/attendance"
import { dailyTeamPendencies, MIN_TEAMS, pendencyLines } from "@/utils/teamDraft"
import { isDailyOpen, type DailyDetail, type TeamDTO } from "@/types/daily"
import { isWritable } from "@/utils/subscription"
import { SUBSCRIPTION_NOTICE_ID } from "@/components/pelada/SubscriptionNotice"

interface TeamsSectionProps {
  daily: DailyDetail
  sortLoading: boolean
  swapLoading: boolean
  selectedPlayer: { id: number; teamId: number } | null
  onSortTeams: () => void
  onPlayerClick: (playerId: number, teamId: number) => void
  currentUserId: number | null
  onTeamNameChange: (teamId: number, name: string) => void
  onTeamColorChange: (teamId: number, color: string) => void
  // opens the "Montar times" editor (admin, before the start)
  onEditTeams: () => void
}

// Teams before the session starts: sort or build by hand (admin), swap players (admin), rename (team player),
// color (team player or admin), and the pending items (short teams, confirmed players without a team, empty teams)
export default function TeamsSection({
  daily,
  sortLoading,
  swapLoading,
  selectedPlayer,
  onSortTeams,
  onPlayerClick,
  currentUserId,
  onTeamNameChange,
  onTeamColorChange,
  onEditTeams,
}: TeamsSectionProps) {
  const [editingTeamId, setEditingTeamId] = useState<number | null>(null)
  const [editingName, setEditingName] = useState("")
  const nameInputRef = useRef<HTMLInputElement>(null)
  const colorDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const open = isDailyOpen(daily.status)
  const isAdminOpen = daily.isAdmin && open
  // a read-only pelada (subscription): the buttons stay, disabled; rename and color go back to plain text
  const writable = isWritable(daily.subscription)
  const notice = writable ? undefined : SUBSCRIPTION_NOTICE_ID
  const full = canSortTeams(daily.confirmedPlayers.length, daily.numberOfTeams, daily.playersPerTeam)
  const hasTeams = daily.teams.length > 0
  const canBuild = daily.confirmedPlayers.length >= MIN_TEAMS
  const pendencies = pendencyLines(dailyTeamPendencies(daily), daily.playersPerTeam)
  const outlineButton = "h-[34px] gap-1.5 bg-transparent px-3.5 hover:bg-accent"

  const startEditing = (team: TeamDTO) => {
    setEditingTeamId(team.id)
    setEditingName(team.name)
    setTimeout(() => nameInputRef.current?.focus(), 0)
  }

  const commitEdit = (teamId: number, originalName: string) => {
    const trimmed = editingName.trim()
    if (trimmed && trimmed !== originalName) onTeamNameChange(teamId, trimmed)
    setEditingTeamId(null)
    setEditingName("")
  }

  const handleColorChange = (teamId: number, color: string) => {
    if (colorDebounceRef.current) clearTimeout(colorDebounceRef.current)
    colorDebounceRef.current = setTimeout(() => onTeamColorChange(teamId, color), 400)
  }

  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-semibold">Times</h2>
        {isAdminOpen && hasTeams && (
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              disabled={sortLoading || swapLoading || !writable}
              aria-describedby={notice}
              onClick={onEditTeams}
              className={outlineButton}
            >
              <HugeiconsIcon icon={PencilEdit02Icon} className="size-3.5" />
              Editar times
            </Button>
            <Button
              variant="outline"
              disabled={!full || sortLoading || swapLoading || !writable}
              aria-describedby={notice}
              onClick={onSortTeams}
              className={outlineButton}
            >
              <HugeiconsIcon icon={ShuffleIcon} className="size-3.5" />
              {sortLoading ? "Sorteando…" : "Sortear de novo"}
            </Button>
          </div>
        )}
      </div>

      {open && hasTeams && pendencies.length > 0 && (
        <Alert>
          <HugeiconsIcon icon={Alert01Icon} className="size-4" />
          <AlertTitle>Pendências</AlertTitle>
          <AlertDescription>
            <ul className="flex flex-col gap-0.5">
              {pendencies.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      {!hasTeams ? (
        <div className="flex flex-col items-center gap-3 rounded-tile border border-dashed border-input px-5 py-7 text-center">
          <HugeiconsIcon icon={ShuffleIcon} className="size-7 text-faint-foreground" strokeWidth={1.8} />
          <div className="flex flex-col gap-1">
            <span className="text-[15px] font-semibold">Sem times ainda</span>
            <span className="max-w-[380px] text-[13px] text-muted-foreground">
              {sortTeamsHint(daily.confirmedPlayers.length, daily.numberOfTeams, daily.playersPerTeam)}
            </span>
          </div>
          {isAdminOpen && (
            <div className="flex flex-wrap justify-center gap-2">
              <Button
                variant="outline"
                disabled={!canBuild || sortLoading || !writable}
                aria-describedby={notice}
                onClick={onEditTeams}
                className="h-[38px] gap-2 bg-transparent px-[18px] font-semibold hover:bg-accent"
              >
                <HugeiconsIcon icon={UserEdit01Icon} className="size-[15px]" />
                Montar manualmente
              </Button>
              <Button
                variant={full ? "brand" : "secondary"}
                disabled={!full || sortLoading || !writable}
                aria-describedby={notice}
                onClick={onSortTeams}
                className={cn("h-[38px] gap-2 px-[18px] font-semibold", !full && "bg-accent text-faint-foreground disabled:opacity-100")}
              >
                <HugeiconsIcon icon={ShuffleIcon} className="size-[15px]" />
                {sortLoading ? "Sorteando…" : "Sortear times"}
              </Button>
            </div>
          )}
        </div>
      ) : (
        <>
          {selectedPlayer !== null && (
            <p className="text-sm text-muted-foreground">
              Jogador selecionado: clique num jogador de outro time para trocar, ou no mesmo jogador para cancelar.
            </p>
          )}
          <div className="grid grid-cols-[repeat(auto-fill,minmax(min(260px,100%),1fr))] gap-3">
            {daily.teams.map((team) => {
              const isOnTeam = currentUserId != null && team.players.some((p) => p.id === currentUserId)
              const canEditColor = open && writable && (isOnTeam || daily.isAdmin)
              const isEditing = editingTeamId === team.id
              return (
                <TeamCard
                  key={team.id}
                  title={
                    <>
                      {canEditColor ? (
                        // shadcn has no color picker: a label around a visually hidden native color input
                        // (relative + size-px: Input's w-full would otherwise stretch the hidden input across the page)
                        <label className="relative shrink-0 cursor-pointer" title="Alterar cor do time">
                          <Input
                            type="color"
                            aria-label={`Cor do ${team.name}`}
                            className="sr-only size-px"
                            defaultValue={team.color ?? "#6b7280"}
                            onChange={(e) => handleColorChange(team.id, e.target.value)}
                          />
                          <TeamColorDot color={team.color} className="size-3" />
                        </label>
                      ) : (
                        <TeamColorDot color={team.color} className="size-3" />
                      )}
                      {isEditing ? (
                        <Input
                          ref={nameInputRef}
                          aria-label="Nome do time"
                          maxLength={30}
                          className="h-7 w-full min-w-0 font-semibold"
                          value={editingName}
                          onChange={(e) => setEditingName(e.target.value)}
                          onBlur={() => commitEdit(team.id, team.name)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") e.currentTarget.blur()
                            if (e.key === "Escape") {
                              setEditingTeamId(null)
                              setEditingName("")
                            }
                          }}
                        />
                      ) : isOnTeam && open && writable ? (
                        <button
                          type="button"
                          title="Renomear time"
                          onClick={() => startEditing(team)}
                          className="truncate text-left text-[15px] font-semibold hover:underline"
                        >
                          {team.name}
                        </button>
                      ) : (
                        <h3 className="truncate text-[15px] font-semibold">{team.name}</h3>
                      )}
                    </>
                  }
                  aside={
                    <span className="flex shrink-0 items-center gap-2">
                      {open && team.players.length < daily.playersPerTeam && (
                        <Badge variant="outline" className="whitespace-nowrap border-warning font-medium text-warning">
                          Desfalcado
                        </Badge>
                      )}
                      <span className="text-xs text-muted-foreground">
                        {team.players.length}/{daily.playersPerTeam}
                      </span>
                      <span className="text-xs font-semibold text-gold">{team.averageStars.toFixed(2)} ★</span>
                    </span>
                  }
                >
                  {team.players.map((player) => {
                    const isSelected = selectedPlayer?.id === player.id
                    return (
                      <TeamPlayerRow key={player.id} className={cn(isSelected && "-mx-2 rounded-lg bg-accent px-2")}>
                        <PlayerAvatar userId={player.id} username={player.username} image={player.image} deleted={player.isDeleted} />
                        <span className="min-w-0 flex-1 truncate text-sm font-medium">{player.username}</span>
                        <StarRow stars={player.stars} />
                        {isAdminOpen && (
                          <Button
                            variant="outline"
                            size="icon"
                            disabled={swapLoading || !writable}
                            aria-describedby={notice}
                            onClick={() => onPlayerClick(player.id, team.id)}
                            aria-label={isSelected ? "Cancelar troca" : `Trocar ${player.username}`}
                            className={cn(
                              "size-[26px] bg-transparent text-subtle-foreground hover:bg-accent hover:text-foreground [&_svg]:size-3",
                              isSelected && "border-primary bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground",
                            )}
                          >
                            <HugeiconsIcon icon={ArrowDataTransferHorizontalIcon} />
                          </Button>
                        )}
                      </TeamPlayerRow>
                    )
                  })}
                </TeamCard>
              )
            })}
          </div>
        </>
      )}
    </section>
  )
}
