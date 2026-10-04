import { Trophy, Upload } from "lucide-react"
import { Button } from "@/components/ui/button"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { TeamColorDot } from "@/components/daily/TeamColorDot"
import { getFileUrl } from "@/lib/utils"
import { championSummary } from "@/utils/finishedSession"
import type { DailyDetail } from "@/types/daily"

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

  return (
    <section className="grid grid-cols-1 gap-4 min-[1100px]:grid-cols-[minmax(0,1.6fr)_minmax(300px,1fr)]">
      <div className="relative min-h-[220px] overflow-hidden rounded-tile bg-card-elevated md:min-h-[340px]">
        {photo ? (
          <>
            <img src={photo} alt="Foto do time campeão" className="absolute inset-0 size-full object-cover" style={{ objectPosition: "center 45%" }} />
            <div className="bg-photo-overlay absolute inset-0" />
          </>
        ) : (
          <div className="absolute inset-0 flex items-center justify-center rounded-tile border border-dashed border-input text-[13px] text-subtle-foreground">
            Sem foto do campeão
          </div>
        )}
        <span
          className={`absolute bottom-3.5 left-4 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-[0.04em] ${photo ? "text-white" : "text-muted-foreground"}`}
        >
          <Trophy className="size-3.5 text-gold" />
          Foto do campeão
        </span>
        {daily.isAdmin && (
          <Button
            variant="ghost"
            disabled={uploadLoading}
            onClick={onChangePhoto}
            className="absolute right-3 top-3 h-8 gap-1.5 bg-black/50 px-3 text-[13px] font-medium text-white hover:bg-black/70 hover:text-white"
          >
            <Upload className="size-3.5" />
            {uploadLoading ? "Enviando…" : photo ? "Trocar foto" : "Enviar foto"}
          </Button>
        )}
      </div>

      {champion && (
        <div className="flex flex-col gap-[18px] rounded-tile border bg-card p-5">
          <div className="flex flex-col gap-1.5">
            <span className="text-[11px] font-semibold uppercase tracking-[0.06em] text-gold">Time campeão</span>
            <div className="flex items-center gap-2.5">
              <TeamColorDot color={champion.team.color} className="size-3.5" />
              <span className="text-2xl font-bold">{champion.team.name}</span>
            </div>
          </div>
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
                <dd className="text-xl font-bold tabular-nums">{value}</dd>
              </div>
            ))}
          </dl>
          <ul className="flex flex-col gap-0.5">
            {champion.team.players.map((player) => {
              const s = statsById.get(player.id)
              return (
                <li key={player.id} className="flex items-center gap-2.5 py-1.5">
                  <PlayerAvatar username={player.username} image={player.image} />
                  <span className="min-w-0 flex-1 truncate text-sm font-medium">{player.username}</span>
                  <span className="text-xs text-muted-foreground">
                    {s?.goals ?? 0} G · {s?.assists ?? 0} A
                  </span>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </section>
  )
}
