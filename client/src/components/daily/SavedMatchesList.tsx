import { TeamColorDot } from "@/components/daily/TeamColorDot"
import { cn } from "@/lib/utils"
import { scorersLine } from "@/utils/liveSession"
import type { DailyDetail } from "@/types/daily"

// Matches saved so far, newest first
export function SavedMatchesList({ daily }: { daily: DailyDetail }) {
  const colorOf = new Map(daily.teams.map((t) => [t.id, t.color]))
  const numbered = daily.matches.map((m, i) => ({ match: m, number: i + 1 })).reverse()

  return (
    <div className="flex flex-col gap-2.5">
      <h2 className="text-base font-semibold">Partidas lançadas</h2>
      {numbered.length === 0 ? (
        <div className="rounded-xl border border-dashed border-input px-4 py-8 text-center text-[13px] text-subtle-foreground">
          Nenhuma partida lançada ainda.
        </div>
      ) : (
        <ul className="flex flex-col gap-2">
          {numbered.map(({ match, number }) => {
            const s1 = match.team1Score ?? 0
            const s2 = match.team2Score ?? 0
            return (
              <li key={match.id} className="flex flex-col gap-1.5 rounded-xl border bg-card px-3.5 py-3">
                <div className="grid grid-cols-[24px_minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2.5 text-sm">
                  <span className="text-xs text-faint-foreground">#{number}</span>
                  <span className={cn("flex min-w-0 items-center gap-2", s1 > s2 && "font-bold")}>
                    <TeamColorDot color={colorOf.get(match.team1Id) ?? null} className="size-[9px]" />
                    <span className="truncate">{match.team1Name}</span>
                  </span>
                  <span className="text-lg font-bold tabular-nums">
                    {s1} – {s2}
                  </span>
                  <span className={cn("flex min-w-0 items-center justify-end gap-2", s2 > s1 && "font-bold")}>
                    <span className="truncate">{match.team2Name}</span>
                    <TeamColorDot color={colorOf.get(match.team2Id) ?? null} className="size-[9px]" />
                  </span>
                </div>
                <span className="pl-[34px] text-xs text-muted-foreground">{scorersLine(match, daily.teams)}</span>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
