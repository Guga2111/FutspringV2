import { HugeiconsIcon } from "@hugeicons/react"
import { ChampionIcon, Upload01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { TeamColorDot } from "@/components/daily/TeamColorDot"
import { getFileUrl } from "@/lib/utils"
import { championSummary } from "@/utils/finishedSession"
import type { DailyDetail } from "@/types/daily"
import { isWritable } from "@/utils/subscription"
import { SUBSCRIPTION_NOTICE_ID } from "@/components/pelada/SubscriptionNotice"

interface ChampionHeroProps {
  daily: DailyDetail
  uploadLoading: boolean
  onChangePhoto: () => void
}

// Champion photo and the champion team card (stacks below 1100 px)
export function ChampionHero({ daily, uploadLoading, onChangePhoto }: ChampionHeroProps) {
  const photo = getFileUrl(daily.championImage)
  const champion = championSummary(daily)
  const statsById = new Map(daily.playerStats.map((s) => [s.userId, s]))

  const writable = isWritable(daily.subscription)
  const uploadButton = daily.isAdmin && (
    <Button variant="outline" size="sm" disabled={uploadLoading || !writable} aria-describedby={writable ? undefined : SUBSCRIPTION_NOTICE_ID} onClick={onChangePhoto} className="gap-1.5 self-start">
      <HugeiconsIcon icon={Upload01Icon} className="size-3.5" />
      {uploadLoading ? "Enviando…" : "Enviar foto do campeão"}
    </Button>
  )

  // without a photo there is no empty frame: the champion card takes the whole row, and admins get the upload there
  if (!photo) {
    if (!champion) return uploadButton || null
    return (
      <section className="grid grid-cols-1 gap-5 rounded-tile border border-highlight/40 bg-card p-5 md:grid-cols-2 md:gap-8">
        <div className="flex flex-col gap-[18px]">
          <ChampionTitle champion={champion} />
          <ChampionStats champion={champion} />
          {uploadButton}
        </div>
        <ChampionPlayers champion={champion} statsById={statsById} />
      </section>
    )
  }

  return (
    <section className="grid grid-cols-1 gap-4 min-[1100px]:grid-cols-[minmax(0,1.6fr)_minmax(300px,1fr)]">
      <div className="relative min-h-[220px] overflow-hidden rounded-tile bg-card-elevated md:min-h-[340px]">
        <img src={photo} alt="Foto do time campeão" className="absolute inset-0 size-full object-cover" style={{ objectPosition: "center 45%" }} />
        <div className="bg-photo-overlay absolute inset-0" />
        <span className="absolute bottom-3.5 left-4 flex items-center gap-1.5 text-xs font-semibold text-white">
          <HugeiconsIcon icon={ChampionIcon} className="size-3.5 text-gold" />
          Foto do campeão
        </span>
        {daily.isAdmin && (
          <Button
            variant="ghost"
            disabled={uploadLoading}
            onClick={onChangePhoto}
            className="absolute right-3 top-3 h-8 gap-1.5 bg-black/50 px-3 text-[13px] font-medium text-white hover:bg-black/70 hover:text-white"
          >
            <HugeiconsIcon icon={Upload01Icon} className="size-3.5" />
            {uploadLoading ? "Enviando…" : "Trocar foto"}
          </Button>
        )}
      </div>

      {champion && (
        <div className="flex flex-col gap-[18px] rounded-tile border border-highlight/40 bg-card p-5">
          <ChampionTitle champion={champion} />
          <ChampionStats champion={champion} />
          <ChampionPlayers champion={champion} statsById={statsById} />
        </div>
      )}
    </section>
  )
}

type Champion = NonNullable<ReturnType<typeof championSummary>>
type PlayerStat = DailyDetail["playerStats"][number]

function ChampionTitle({ champion }: { champion: Champion }) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-caption font-semibold text-highlight-text">Time campeão</span>
      <div className="flex items-center gap-2.5">
        <TeamColorDot color={champion.team.color} className="size-3.5" />
        <span className="font-display text-heading-lg font-bold">{champion.team.name}</span>
      </div>
    </div>
  )
}

function ChampionStats({ champion }: { champion: Champion }) {
  return (
    <dl className="grid grid-cols-3 gap-2">
      {(
        [
          ["pontos", String(champion.entry.points)],
          ["V–E–D", `${champion.entry.wins}–${champion.entry.draws}–${champion.entry.losses}`],
          ["aproveit.", `${champion.winRate}%`],
        ] as const
      ).map(([label, value]) => (
        <div key={label} className="flex flex-col-reverse gap-0.5 rounded-[10px] bg-accent px-3 py-2.5">
          <dt className="text-[11px] text-muted-foreground">{label}</dt>
          <dd className="font-display text-heading font-bold tabular-nums">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function ChampionPlayers({ champion, statsById }: { champion: Champion; statsById: Map<number, PlayerStat> }) {
  return (
    <ul className="flex flex-col gap-0.5">
      {champion.team.players.map((player) => {
        const s = statsById.get(player.id)
        return (
          <li key={player.id} className="flex items-center gap-2.5 py-1.5">
            <PlayerAvatar userId={player.id} username={player.username} image={player.image} deleted={player.isDeleted} />
            <span className="min-w-0 flex-1 truncate text-sm font-medium">{player.username}</span>
            <span className="text-xs text-muted-foreground">
              {s?.goals ?? 0} G · {s?.assists ?? 0} A
            </span>
          </li>
        )
      })}
    </ul>
  )
}
