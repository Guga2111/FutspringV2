import { useMemo } from "react"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { TeamCard, TeamPlayerRow } from "@/components/daily/TeamCard"
import { TeamColorDot } from "@/components/daily/TeamColorDot"
import { playerTotals } from "@/utils/liveSession"
import type { DailyDetail } from "@/types/daily"

// Teams during the live session, with each player's goals and assists so far
export function LiveTeamsSection({ daily }: { daily: DailyDetail }) {
  const totals = useMemo(() => playerTotals(daily.matches), [daily.matches])
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-base font-semibold">Times</h2>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(240px,100%),1fr))] gap-3">
        {daily.teams.map((team) => (
          <TeamCard
            key={team.id}
            title={
              <>
                <TeamColorDot color={team.color} className="size-3" />
                <h3 className="truncate text-[15px] font-semibold">{team.name}</h3>
              </>
            }
          >
            {team.players.map((player) => {
              const t = totals.get(player.id)
              return (
                <TeamPlayerRow key={player.id} className="py-1.5">
                  <PlayerAvatar username={player.username} image={player.image} className="size-[26px] text-[10px]" />
                  <span className="min-w-0 flex-1 truncate text-sm">{player.username}</span>
                  {t && (
                    <span className="text-xs text-muted-foreground">
                      {t.goals} G · {t.assists} A
                    </span>
                  )}
                </TeamPlayerRow>
              )
            })}
          </TeamCard>
        ))}
      </div>
    </section>
  )
}
