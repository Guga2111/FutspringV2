import { useRef, useState } from 'react'
import { ArrowLeftRight, ChevronDown, ChevronRight } from 'lucide-react'
import type { DailyDetail, TeamDTO, PlayerDTO } from '../../types/daily'
import { getFileUrl } from '../../lib/utils'
import { Button } from '../ui/button'
import { Avatar, AvatarImage, AvatarFallback } from '../ui/avatar'
import StarRating from './StarRating'

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
}

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
}: TeamsSectionProps) {
  const [expanded, setExpanded] = useState(false)
  const [editingTeamId, setEditingTeamId] = useState<number | null>(null)
  const [editingName, setEditingName] = useState('')
  const nameInputRef = useRef<HTMLInputElement>(null)
  const colorDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const required = daily.numberOfTeams * daily.playersPerTeam
  const canSort =
    daily.isAdmin &&
    (daily.status === 'SCHEDULED' || daily.status === 'CONFIRMED') &&
    daily.confirmedPlayers.length === required

  const hasTeams = daily.teams.length > 0

  const showSection = canSort || hasTeams ||
    (daily.isAdmin && (daily.status === 'SCHEDULED' || daily.status === 'CONFIRMED'))
  if (!showSection) return null

  const startEditing = (team: TeamDTO) => {
    setEditingTeamId(team.id)
    setEditingName(team.name)
    setTimeout(() => nameInputRef.current?.focus(), 0)
  }

  const commitEdit = (teamId: number, originalName: string) => {
    const trimmed = editingName.trim()
    if (trimmed && trimmed !== originalName) {
      onTeamNameChange(teamId, trimmed)
    }
    setEditingTeamId(null)
    setEditingName('')
  }

  const handleColorChange = (teamId: number, color: string) => {
    if (colorDebounceRef.current) clearTimeout(colorDebounceRef.current)
    colorDebounceRef.current = setTimeout(() => {
      onTeamColorChange(teamId, color)
    }, 400)
  }

  return (
    <section className="mb-8">
      <div className="flex items-center justify-between mb-3">
        <Button variant="ghost" size="sm" className="h-7 gap-1 text-lg font-semibold px-0 hover:bg-transparent" onClick={() => setExpanded((p) => !p)}>
          {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          Times
        </Button>
        {daily.isAdmin && (daily.status === 'SCHEDULED' || daily.status === 'CONFIRMED') && (
          canSort ? (
            <Button
              variant="gradient"
              size="sm"
              disabled={sortLoading || swapLoading}
              onClick={onSortTeams}
            >
              {sortLoading ? 'Sorteando...' : hasTeams ? 'Re-sorteie os times' : 'Sortear Times'}
            </Button>
          ) : (
            <span className="text-xs text-muted-foreground">
              É necessário exatamente {required} jogadores ({daily.numberOfTeams}×{daily.playersPerTeam})
            </span>
          )
        )}
      </div>
      {expanded && selectedPlayer !== null && (
        <p className="text-sm text-muted-foreground mb-3">
          Jogador Selecionado — clique num jogador de time diferente para trocar, ou clicar no mesmo jogador pra cancelar.
        </p>
      )}
      {expanded && (hasTeams ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {daily.teams.map((team: TeamDTO) => {
            const isOnTeam = currentUserId != null && team.players.some((p) => p.id === currentUserId)
            const canEditColor = isOnTeam || daily.isAdmin
            const isEditing = editingTeamId === team.id

            return (
              <div key={team.id} className="border rounded-lg overflow-hidden">
                <div className="px-4 py-2 flex items-center justify-between gap-2 bg-muted/50">
                  <div className="flex items-center gap-2 min-w-0 flex-1">
                    {canEditColor && (
                      <label className="cursor-pointer flex-shrink-0" title="Alterar cor do time">
                        <input
                          type="color"
                          className="sr-only"
                          defaultValue={team.color ?? '#6b7280'}
                          onChange={(e) => handleColorChange(team.id, e.target.value)}
                        />
                        <span
                          className="inline-block h-4 w-4 rounded-full border border-current"
                          style={{ background: team.color ?? '#6b7280' }}
                        />
                      </label>
                    )}
                    {isEditing ? (
                      <input
                        ref={nameInputRef}
                        className="font-semibold bg-transparent border-b border-current outline-none w-full min-w-0"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        onBlur={() => commitEdit(team.id, team.name)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') e.currentTarget.blur()
                          if (e.key === 'Escape') {
                            setEditingTeamId(null)
                            setEditingName('')
                          }
                        }}
                      />
                    ) : (
                      <h3
                        className={`font-semibold truncate ${isOnTeam ? 'cursor-pointer hover:underline' : ''}`}
                        onClick={() => isOnTeam && startEditing(team)}
                      >
                        {team.name}
                      </h3>
                    )}
                  </div>
                  <span className="text-xs text-yellow-400 flex-shrink-0">
                    {team.averageStars.toFixed(2)} ★
                  </span>
                </div>
                <div className="p-3 space-y-1">
                  {team.players.map((player: PlayerDTO) => {
                    const isSelected = selectedPlayer?.id === player.id
                    return (
                      <div
                        key={player.id}
                        className={`flex items-center gap-2 p-1.5 rounded ${isSelected ? 'bg-primary/10 ring-1 ring-primary' : 'hover:bg-muted'}`}
                      >
                        <Avatar className="h-8 w-8 flex-shrink-0">
                          {player.image ? (
                            <AvatarImage src={getFileUrl(player.image)} alt={player.username} />
                          ) : null}
                          <AvatarFallback className="text-xs font-semibold">
                            {player.username.slice(0, 2).toUpperCase()}
                          </AvatarFallback>
                        </Avatar>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium truncate">{player.username}</p>
                        </div>
                        <StarRating stars={player.stars} />
                        {daily.isAdmin && (
                          <Button
                            size="icon"
                            variant="ghost"
                            className={`h-6 w-6 rounded-full border ${isSelected ? 'bg-primary text-primary-foreground border-primary' : 'border-muted-foreground text-muted-foreground'}`}
                            disabled={swapLoading}
                            onClick={() => onPlayerClick(player.id, team.id)}
                            aria-label={isSelected ? 'Cancelar troca' : 'Mover ou trocar jogador'}
                          >
                            <ArrowLeftRight className="h-3 w-3" />
                          </Button>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">Sem times ainda. Use "Sorteie Times" para auto-balancear.</p>
      ))}
    </section>
  )
}
