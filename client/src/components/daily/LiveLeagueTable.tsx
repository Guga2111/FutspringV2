import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { TeamColorDot } from "@/components/daily/TeamColorDot"
import { cn } from "@/lib/utils"
import { formatGoalDiff, goalDiffClass } from "@/utils/liveSession"
import type { DailyDetail } from "@/types/daily"

const COLS = [
  { key: "played", label: "J", width: "w-[30px]" },
  { key: "wins", label: "V", width: "w-[30px]" },
  { key: "draws", label: "E", width: "w-[30px]" },
  { key: "losses", label: "D", width: "w-[30px]" },
] as const

// The table the API keeps up to date while the session is live (every team, in the server's order)
export function LiveLeagueTable({ daily }: { daily: DailyDetail }) {
  const colorOf = new Map(daily.teams.map((t) => [t.id, t.color]))
  const rows = daily.leagueTable.length > 0
    ? daily.leagueTable
    : daily.teams.map((t, i) => ({
        teamId: t.id, teamName: t.name, position: i + 1, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, goalDiff: 0, points: 0,
      }))

  return (
    <div className="flex flex-col gap-2.5">
      <h2 className="text-base font-semibold">Classificação ao vivo</h2>
      <div className="overflow-hidden rounded-xl border">
        <Table className="table-fixed">
          <TableHeader className="bg-card">
            <TableRow className="hover:bg-transparent">
              <TableHead className="h-auto w-7 px-3 py-2.5 text-xs font-normal text-subtle-foreground">#</TableHead>
              <TableHead className="h-auto px-1 py-2.5 text-xs font-normal text-subtle-foreground">Time</TableHead>
              {COLS.map((c) => (
                <TableHead key={c.key} className={cn("h-auto px-0 py-2.5 text-center text-xs font-normal text-subtle-foreground", c.width)}>
                  {c.label}
                </TableHead>
              ))}
              <TableHead className="h-auto w-9 px-0 py-2.5 text-center text-xs font-normal text-subtle-foreground">SG</TableHead>
              <TableHead className="h-auto w-[52px] py-2.5 pl-0 pr-3 text-center text-xs font-normal text-foreground">Pts</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => {
              const values = { played: row.wins + row.draws + row.losses, wins: row.wins, draws: row.draws, losses: row.losses }
              return (
                <TableRow key={row.teamId} className="border-border/60 text-sm hover:bg-transparent">
                  <TableCell className="px-3 py-[11px] font-bold text-subtle-foreground">{row.position}</TableCell>
                  <TableCell className="px-1 py-[11px]">
                    <span className="flex min-w-0 items-center gap-2 font-medium">
                      <TeamColorDot color={colorOf.get(row.teamId) ?? null} />
                      <span className="truncate">{row.teamName}</span>
                    </span>
                  </TableCell>
                  {COLS.map((c) => (
                    <TableCell key={c.key} className="px-0 py-[11px] text-center tabular-nums">{values[c.key]}</TableCell>
                  ))}
                  <TableCell className={cn("px-0 py-[11px] text-center tabular-nums", goalDiffClass(row.goalDiff))}>
                    {formatGoalDiff(row.goalDiff)}
                  </TableCell>
                  <TableCell className="py-[11px] pl-0 pr-3 text-center font-bold tabular-nums">{row.points}</TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
