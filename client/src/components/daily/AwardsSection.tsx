import type { DailyDetail } from '../../types/daily'
import PlayerAvatar from './PlayerAvatar'
import { Target, ThumbsDown, Brush, HandHelping } from 'lucide-react'

interface AwardsSectionProps {
  daily: DailyDetail
}

function AwardCard({
  icon,
  label,
  winnerIds,
  winnerNames,
  confirmedPlayers,
}: {
  icon: React.ReactNode
  label: string
  winnerIds: number[]
  winnerNames: string[]
  confirmedPlayers: DailyDetail['confirmedPlayers']
}) {
  if (!winnerNames?.length) return null

  return (
    <div className="border-2 rounded-lg p-4 flex items-center gap-3">
      {winnerIds.length === 1 ? (
        (() => {
          const p = confirmedPlayers.find(cp => cp.id === winnerIds[0])
          return p ? <PlayerAvatar player={p} /> : <div className="h-10 w-10 rounded-full bg-muted flex-shrink-0" />
        })()
      ) : (
        <div className="h-10 w-10 rounded-full bg-muted flex-shrink-0 flex items-center justify-center">
          {icon}
        </div>
      )}
      <div>
        <p className="text-xs text-muted-foreground flex items-center gap-1">{icon} {label}</p>
        <p className="font-semibold">{winnerNames.join(', ')}</p>
      </div>
    </div>
  )
}

export default function AwardsSection({ daily }: AwardsSectionProps) {
  if (!daily.award) return null

  const { award } = daily
  const hasAnyAward =
    award.puskasWinnerNames?.length > 0 ||
    award.wiltballWinnerNames?.length > 0 ||
    award.artilheiroWinnerNames?.length > 0 ||
    award.garcomWinnerNames?.length > 0

  if (!hasAnyAward) return null

  return (
    <section className="mb-8">
      <h2 className="text-lg font-semibold mb-3">Prêmios</h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <AwardCard
          icon={<Brush className="h-3.5 w-3.5 text-amber-400" />}
          label="Puskas"
          winnerIds={award.puskasWinnerIds}
          winnerNames={award.puskasWinnerNames}
          confirmedPlayers={daily.confirmedPlayers}
        />
        <AwardCard
          icon={<ThumbsDown className="h-3.5 w-3.5 text-red-500" />}
          label="Bola Murcha"
          winnerIds={award.wiltballWinnerIds}
          winnerNames={award.wiltballWinnerNames}
          confirmedPlayers={daily.confirmedPlayers}
        />
        <AwardCard
          icon={<Target className="h-3.5 w-3.5 text-green-500" />}
          label="Artilheiro"
          winnerIds={award.artilheiroWinnerIds}
          winnerNames={award.artilheiroWinnerNames}
          confirmedPlayers={daily.confirmedPlayers}
        />
        <AwardCard
          icon={<HandHelping className="h-3 w-3 text-green-200" />}
          label="Garçom"
          winnerIds={award.garcomWinnerIds}
          winnerNames={award.garcomWinnerNames}
          confirmedPlayers={daily.confirmedPlayers}
        />
      </div>
    </section>
  )
}
