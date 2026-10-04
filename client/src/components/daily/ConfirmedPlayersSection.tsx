import { useState } from 'react'
import { Link } from 'react-router-dom'
import { UserPlus, UserMinus, Loader2, ChevronDown, ChevronRight } from 'lucide-react'
import type { DailyDetail, PlayerDTO } from '../../types/daily'
import { getFileUrl } from '../../lib/utils'
import { Badge } from '../ui/badge'
import { Button } from '../ui/button'
import { Avatar, AvatarImage, AvatarFallback } from '../ui/avatar'
import StarRating from './StarRating'

interface ConfirmedPlayersSectionProps {
  daily: DailyDetail
  adminToggleLoading: number | null
  onAdminConfirm: (userId: number) => void
  onAdminDisconfirm: (userId: number) => void
}

function PlayerRow({
  player,
  confirmed,
  canAdminToggle,
  adminToggleLoading,
  onAdminConfirm,
  onAdminDisconfirm,
}: {
  player: PlayerDTO
  confirmed: boolean
  canAdminToggle: boolean
  adminToggleLoading: number | null
  onAdminConfirm: (userId: number) => void
  onAdminDisconfirm: (userId: number) => void
}) {
  const isLoading = adminToggleLoading === player.id

  return (
    <div className="flex items-center gap-3 p-2 rounded hover:bg-muted">
      <Avatar className="h-10 w-10 flex-shrink-0">
        {player.image ? (
          <AvatarImage src={getFileUrl(player.image)} alt={player.username} />
        ) : null}
        <AvatarFallback className="text-sm font-semibold">
          {player.username.slice(0, 2).toUpperCase()}
        </AvatarFallback>
      </Avatar>
      <div className="flex-1 min-w-0 flex items-center gap-2 flex-wrap">
        <Link
          to={`/profile/${player.id}`}
          className="text-sm font-medium hover:underline"
        >
          {player.username}
        </Link>
        {player.position && (
          <Badge variant="secondary" className="text-xs">
            {player.position}
          </Badge>
        )}
      </div>
      <StarRating stars={player.stars} />
      {canAdminToggle && (
        <Button
          size="icon"
          variant="ghost"
          className="h-7 w-7 ml-1"
          disabled={isLoading}
          onClick={() => confirmed ? onAdminDisconfirm(player.id) : onAdminConfirm(player.id)}
          title={confirmed ? 'Remover presença' : 'Confirmar presença'}
        >
          {isLoading ? (
            <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
          ) : confirmed ? (
            <UserMinus className="h-4 w-4 text-destructive" />
          ) : (
            <UserPlus className="h-4 w-4 text-muted-foreground" />
          )}
        </Button>
      )}
    </div>
  )
}

export default function ConfirmedPlayersSection({
  daily,
  adminToggleLoading,
  onAdminConfirm,
  onAdminDisconfirm,
}: ConfirmedPlayersSectionProps) {
  const [expanded, setExpanded] = useState(false)

  const status = daily.status
  const canAdminToggle =
    daily.isAdmin &&
    (status === 'SCHEDULED' || status === 'CONFIRMED') &&
    daily.peladaMembers != null

  const title = canAdminToggle && daily.peladaMembers
    ? `Todos Membros (${daily.peladaMembers.filter((p) => daily.confirmedPlayers.some((c) => c.id === p.id)).length} / ${daily.peladaMembers.length} confirmados)`
    : `Jogadores Confirmados (${daily.confirmedPlayers.length}/${daily.numberOfTeams * daily.playersPerTeam})`

  if (canAdminToggle && daily.peladaMembers) {
    const confirmedIds = new Set(daily.confirmedPlayers.map((p) => p.id))
    const confirmed = daily.peladaMembers
      .filter((p) => confirmedIds.has(p.id))
      .sort((a, b) => a.username.localeCompare(b.username))
    const unconfirmed = daily.peladaMembers
      .filter((p) => !confirmedIds.has(p.id))
      .sort((a, b) => a.username.localeCompare(b.username))
    const allSorted = [...confirmed, ...unconfirmed]

    return (
      <section className="mb-8">
        <div className="flex items-center justify-between mb-3">
          <Button variant="ghost" size="sm" className="h-7 gap-1 text-lg font-semibold px-0 hover:bg-transparent" onClick={() => setExpanded((p) => !p)}>
            {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
            {title}
          </Button>
        </div>
        {expanded && (
          allSorted.length === 0 ? (
            <p className="text-sm text-muted-foreground">Sem membros ainda.</p>
          ) : (
            <div className="space-y-2">
              {allSorted.map((player, idx) => (
                <div key={player.id}>
                  {idx === confirmed.length && unconfirmed.length > 0 && confirmed.length > 0 && (
                    <div className="border-t my-2" />
                  )}
                  <PlayerRow
                    player={player}
                    confirmed={confirmedIds.has(player.id)}
                    canAdminToggle={true}
                    adminToggleLoading={adminToggleLoading}
                    onAdminConfirm={onAdminConfirm}
                    onAdminDisconfirm={onAdminDisconfirm}
                  />
                </div>
              ))}
            </div>
          )
        )}
      </section>
    )
  }

  return (
    <section className="mb-8">
      <div className="flex items-center justify-between mb-3">
        <Button variant="ghost" size="sm" className="h-7 gap-1 text-lg font-semibold px-0 hover:bg-transparent" onClick={() => setExpanded((p) => !p)}>
          {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          {title}
        </Button>
      </div>
      {expanded && (
        daily.confirmedPlayers.length === 0 ? (
          <p className="text-sm text-muted-foreground">Sem jogadores confirmados ainda.</p>
        ) : (
          <div className="space-y-2">
            {daily.confirmedPlayers.map((player) => (
              <PlayerRow
                key={player.id}
                player={player}
                confirmed={true}
                canAdminToggle={false}
                adminToggleLoading={adminToggleLoading}
                onAdminConfirm={onAdminConfirm}
                onAdminDisconfirm={onAdminDisconfirm}
              />
            ))}
          </div>
        )
      )}
    </section>
  )
}
