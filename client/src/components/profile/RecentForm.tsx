import { format } from "date-fns"
import { Link } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { cn } from "@/lib/utils"
import type { MatchHistoryRow } from "@/types/stats"
import { parseLocalDate } from "@/utils/dates"
import { recentForm, sessionResultLabel, toSessionResult } from "@/utils/profile"

interface RecentFormProps {
  matchHistory: MatchHistoryRow[]
  loading: boolean
}

const FORM_SIZE = 5

// The last five sessions as tiles, newest first: champion tiles in the highlight, each links to the session
export function RecentForm({ matchHistory, loading }: RecentFormProps) {
  const rows = recentForm(matchHistory, FORM_SIZE)
  const titles = rows.filter((row) => toSessionResult(row.result) === "W").length

  return (
    <Card>
      <CardHeader className="flex-row items-baseline justify-between gap-3 space-y-0 p-4 pb-3 md:p-5 md:pb-3">
        <CardTitle className="text-title font-semibold">Forma recente</CardTitle>
        {!loading && rows.length > 0 && (
          <p className="text-body-sm text-muted-foreground">
            {titles === 1 ? "1 título" : `${titles} títulos`} nas últimas {rows.length}
          </p>
        )}
      </CardHeader>
      <CardContent className="p-4 pt-0 md:p-5 md:pt-0">
        {loading ? (
          <div className="grid grid-cols-5 gap-2">
            {Array.from({ length: FORM_SIZE }).map((_, i) => (
              <Skeleton key={i} className="h-[68px] rounded-tile" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <p className="text-body text-muted-foreground">Nenhuma sessão ainda.</p>
        ) : (
          // the tiles share the row (at least three columns, so one session isn't a full-width bar)
          <ol className="grid gap-2" style={{ gridTemplateColumns: `repeat(${Math.max(rows.length, 3)}, minmax(0, 1fr))` }}>
            {rows.map((row) => {
              const result = toSessionResult(row.result)
              const won = result === "W"
              const date = format(parseLocalDate(row.date), "dd/MM")
              return (
                <li key={row.dailyId}>
                  <Link
                    to={`/daily/${row.dailyId}`}
                    aria-label={`${date}: ${sessionResultLabel[result]}, ${row.goals} gols, ${row.assists} assistências`}
                    className={cn(
                      "flex h-[68px] flex-col items-center justify-center gap-1 rounded-tile transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                      won
                        ? "bg-highlight-muted text-highlight-text hover:bg-highlight/25"
                        : "bg-muted text-muted-foreground hover:bg-accent hover:text-foreground",
                    )}
                  >
                    <span className={cn("font-display text-title font-bold tabular-nums", won ? "text-highlight-text" : "text-foreground")}>
                      {date}
                    </span>
                    <span className="text-caption tabular-nums">
                      {row.goals}G · {row.assists}A
                    </span>
                  </Link>
                </li>
              )
            })}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}
