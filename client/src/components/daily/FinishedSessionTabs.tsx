import { useMemo, useState } from "react"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { StarRow } from "@/components/StarRow"
import { TeamCard, TeamPlayerRow } from "@/components/daily/TeamCard"
import { TeamColorDot } from "@/components/daily/TeamColorDot"
import { cn } from "@/lib/utils"
import { matchStatNames, sortPlayerStats, type PlayerSortKey } from "@/utils/finishedSession"
import { formatGoalDiff, goalDiffClass } from "@/utils/liveSession"
import type { DailyDetail } from "@/types/daily"

const MEDALS = ["text-gold", "text-silver", "text-bronze"] as const
const INITIAL_MATCHES = 6

// Final table, matches, players and teams of a finished session (replaces the old stacked sections)
export function FinishedSessionTabs({ daily }: { daily: DailyDetail }) {
  return (
    <Tabs defaultValue="table" className="flex flex-col gap-4">
      <TabsList variant="pill" className="self-start">
        <TabsTrigger variant="pill" value="table">Classificação</TabsTrigger>
        <TabsTrigger variant="pill" value="matches">Partidas</TabsTrigger>
        <TabsTrigger variant="pill" value="players">Jogadores</TabsTrigger>
        <TabsTrigger variant="pill" value="teams">Times</TabsTrigger>
      </TabsList>
      <TabsContent value="table" className="mt-0">
        <FinalLeagueTable daily={daily} />
      </TabsContent>
      <TabsContent value="matches" className="mt-0">
        <MatchCards daily={daily} />
      </TabsContent>
      <TabsContent value="players" className="mt-0">
        <PlayerStatsTable daily={daily} />
      </TabsContent>
      <TabsContent value="teams" className="mt-0">
        <FinalTeams daily={daily} />
      </TabsContent>
    </Tabs>
  )
}

const head = "h-auto px-0 py-2.5 text-center text-xs font-normal text-subtle-foreground"
const num = "px-0 py-3 text-center tabular-nums"

function FinalLeagueTable({ daily }: { daily: DailyDetail }) {
  const colorOf = new Map(daily.teams.map((t) => [t.id, t.color]))
  const cols = ["V", "E", "D", "GP", "GC", "SG"] as const
  return (
    <div className="overflow-hidden rounded-xl border">
      <Table className="table-fixed">
        <TableHeader className="bg-card">
          <TableRow className="hover:bg-transparent">
            <TableHead className={cn(head, "w-6 pl-3 md:w-11")}>#</TableHead>
            <TableHead className={cn(head, "pl-2 text-left md:pl-3")}>Time</TableHead>
            {cols.map((c) => (
              <TableHead key={c} className={cn(head, c === "GP" || c === "GC" ? "w-7 md:w-14" : c === "SG" ? "w-[30px] md:w-14" : "w-6 md:w-14")}>
                {c}
              </TableHead>
            ))}
            <TableHead className={cn(head, "w-8 pr-3 text-foreground md:w-16")}>Pts</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {daily.leagueTable.map((row, i) => (
            <TableRow key={row.teamId} className={cn("border-border/60 text-sm hover:bg-transparent", i === 0 && "bg-gold-muted hover:bg-gold-muted")}>
              <TableCell className={cn(num, "pl-3 font-bold", MEDALS[i] ?? "text-faint-foreground")}>{row.position}º</TableCell>
              <TableCell className="py-3 pl-2 pr-1 md:pl-3">
                <span className={cn("flex min-w-0 items-center gap-2", i === 0 ? "font-bold" : "font-medium")}>
                  <TeamColorDot color={colorOf.get(row.teamId) ?? null} />
                  <span className="truncate">{row.teamName}</span>
                </span>
              </TableCell>
              <TableCell className={num}>{row.wins}</TableCell>
              <TableCell className={num}>{row.draws}</TableCell>
              <TableCell className={num}>{row.losses}</TableCell>
              <TableCell className={num}>{row.goalsFor}</TableCell>
              <TableCell className={num}>{row.goalsAgainst}</TableCell>
              <TableCell className={cn(num, goalDiffClass(row.goalDiff))}>{formatGoalDiff(row.goalDiff)}</TableCell>
              <TableCell className={cn(num, "pr-3 font-bold")}>{row.points}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

function MatchCards({ daily }: { daily: DailyDetail }) {
  const [showAll, setShowAll] = useState(false)
  const colorOf = new Map(daily.teams.map((t) => [t.id, t.color]))
  const visible = showAll ? daily.matches : daily.matches.slice(0, INITIAL_MATCHES)
  if (daily.matches.length === 0) {
    return <p className="text-sm text-subtle-foreground">Nenhuma partida lançada.</p>
  }
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(300px,100%),1fr))] gap-3">
        {visible.map((match, i) => {
          const s1 = match.team1Score ?? 0
          const s2 = match.team2Score ?? 0
          return (
            <article key={match.id} className="flex flex-col gap-3 rounded-xl border bg-card p-3.5">
              <span className="text-[11px] font-semibold uppercase tracking-[0.06em] text-faint-foreground">Partida {i + 1}</span>
              <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2.5 text-[15px]">
                <span className={cn("flex min-w-0 items-center gap-2", s1 > s2 && "font-bold")}>
                  <TeamColorDot color={colorOf.get(match.team1Id) ?? null} />
                  <span className="truncate">{match.team1Name}</span>
                </span>
                <span className="text-[22px] font-bold tabular-nums tracking-[0.02em]">
                  {s1} – {s2}
                </span>
                <span className={cn("flex min-w-0 items-center justify-end gap-2", s2 > s1 && "font-bold")}>
                  <span className="truncate">{match.team2Name}</span>
                  <TeamColorDot color={colorOf.get(match.team2Id) ?? null} />
                </span>
              </div>
              <div className="flex flex-col gap-1 border-t pt-2.5 text-[13px] text-secondary-foreground">
                <span>
                  <span className="text-subtle-foreground">Gols </span>
                  {matchStatNames(match, daily.teams, "goals")}
                </span>
                <span>
                  <span className="text-subtle-foreground">Assist. </span>
                  {matchStatNames(match, daily.teams, "assists")}
                </span>
              </div>
            </article>
          )
        })}
      </div>
      {daily.matches.length > INITIAL_MATCHES && (
        <Button variant="outline" onClick={() => setShowAll((v) => !v)} className="h-9 self-center bg-transparent px-4 hover:bg-accent">
          {showAll ? "Mostrar menos" : `Mostrar todas as ${daily.matches.length} partidas`}
        </Button>
      )}
    </div>
  )
}

const SORT_COLUMNS: { key: PlayerSortKey; label: string }[] = [
  { key: "goals", label: "Gols" },
  { key: "assists", label: "Assist." },
  { key: "matchesPlayed", label: "Partidas" },
  { key: "wins", label: "Vitórias" },
]

function PlayerStatsTable({ daily }: { daily: DailyDetail }) {
  const [sortKey, setSortKey] = useState<PlayerSortKey>("goals")
  const rows = useMemo(() => sortPlayerStats(daily.playerStats, sortKey), [daily.playerStats, sortKey])
  const playerInfo = useMemo(
    () => new Map(daily.teams.flatMap((t) => t.players.map((p) => [p.id, { image: p.image, color: t.color }] as const))),
    [daily.teams],
  )
  return (
    <div className="overflow-hidden rounded-xl border">
      <Table className="table-fixed">
        <TableHeader className="bg-card">
          <TableRow className="hover:bg-transparent">
            <TableHead className="h-auto px-3 py-1.5 text-xs font-normal text-subtle-foreground">Jogador</TableHead>
            {SORT_COLUMNS.map(({ key, label }) => (
              <TableHead
                key={key}
                aria-sort={sortKey === key ? "descending" : "none"}
                className={cn("h-auto px-0 py-1.5 text-center", key === "goals" ? "w-11 md:w-[90px]" : key === "assists" ? "w-12 md:w-[90px]" : "w-[52px] md:w-[90px]")}
              >
                <button
                  type="button"
                  onClick={() => setSortKey(key)}
                  className={cn(
                    "py-1.5 text-xs",
                    sortKey === key ? "font-semibold text-foreground" : "font-normal text-subtle-foreground hover:text-foreground",
                  )}
                >
                  {label}
                  {sortKey === key && " ↓"}
                </button>
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => {
            const info = playerInfo.get(row.userId)
            return (
              <TableRow key={row.userId} className="border-border/60 text-sm hover:bg-row-hover">
                <TableCell className="px-3 py-2.5">
                  <span className="flex min-w-0 items-center gap-2.5">
                    <span className="relative shrink-0">
                      <PlayerAvatar username={row.username} image={info?.image ?? null} />
                      <TeamColorDot
                        color={info?.color ?? null}
                        className="absolute -bottom-px -right-px border-2 border-background"
                      />
                    </span>
                    <span className="truncate font-medium">{row.username}</span>
                  </span>
                </TableCell>
                {SORT_COLUMNS.map(({ key }) => (
                  <TableCell key={key} className={cn("px-0 py-2.5 text-center tabular-nums", sortKey === key && "font-semibold")}>
                    {row[key]}
                  </TableCell>
                ))}
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}

function FinalTeams({ daily }: { daily: DailyDetail }) {
  const positionOf = new Map(daily.leagueTable.map((e) => [e.teamId, e.position]))
  const teams = [...daily.teams].sort((a, b) => (positionOf.get(a.id) ?? 99) - (positionOf.get(b.id) ?? 99))
  return (
    <div className="grid grid-cols-[repeat(auto-fill,minmax(min(280px,100%),1fr))] gap-3">
      {teams.map((team) => (
        <TeamCard
          key={team.id}
          title={
            <>
              <TeamColorDot color={team.color} className="size-3" />
              <h3 className="truncate text-[15px] font-semibold">{team.name}</h3>
            </>
          }
          aside={
            <>
              {positionOf.has(team.id) && <span className="text-xs text-muted-foreground">{positionOf.get(team.id)}º lugar</span>}
              <span className="text-xs font-semibold text-gold">{team.averageStars.toFixed(2)} ★</span>
            </>
          }
        >
          {team.players.map((player) => (
            <TeamPlayerRow key={player.id}>
              <PlayerAvatar username={player.username} image={player.image} />
              <span className="min-w-0 flex-1 truncate text-sm font-medium">{player.username}</span>
              <StarRow stars={player.stars} />
            </TeamPlayerRow>
          ))}
        </TeamCard>
      ))}
    </div>
  )
}
