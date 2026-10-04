import { useMemo, useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { format, parseISO } from "date-fns"
import { ptBR } from "date-fns/locale"
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts"
import { ExternalLink, Trophy } from "lucide-react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { cn, getFileUrl, getPeladaInitials } from "@/lib/utils"
import { usePlayerPeladaHistory } from "@/components/pelada/hooks/usePlayerPeladaHistory"
import type { PeladaMember, PlayerPeladaHistoryRow } from "@/types/pelada"

interface PlayerHistoryDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  peladaId: number
  members: PeladaMember[]
  // null opens the dialog with the player selector empty
  initialUserId: number | null
}

// How many of the most recent sessions to request from the backend; "all" requests every session
const PERIODS = [
  { value: "5", label: "Últimas 5" },
  { value: "10", label: "Últimas 10" },
  { value: "20", label: "Últimas 20" },
  { value: "all", label: "Todas" },
] as const
type Period = (typeof PERIODS)[number]["value"]

function summarize(rows: PlayerPeladaHistoryRow[]) {
  return rows.reduce(
    (acc, row) => ({
      sessions: acc.sessions + 1,
      goals: acc.goals + row.goals,
      assists: acc.assists + row.assists,
      matchWins: acc.matchWins + row.wins,
      titles: acc.titles + (row.wonSession ? 1 : 0),
    }),
    { sessions: 0, goals: 0, assists: 0, matchWins: 0, titles: 0 }
  )
}

const chartConfig = {
  goals: { label: "Gols", color: "hsl(var(--chart-1))" },
  assists: { label: "Assistências", color: "hsl(var(--chart-2))" },
} satisfies ChartConfig

function formatDay(date: string) {
  return format(parseISO(date), "dd MMM", { locale: ptBR })
}

function formatFullDate(date: string) {
  return format(parseISO(date), "dd 'de' MMMM 'de' yyyy", { locale: ptBR })
}

export function PlayerHistoryDialog({
  open,
  onOpenChange,
  peladaId,
  members,
  initialUserId,
}: PlayerHistoryDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        {open && (
          // Remounts on every open, so the selector starts from initialUserId
          <PlayerHistoryContent
            peladaId={peladaId}
            members={members}
            initialUserId={initialUserId}
            onNavigate={() => onOpenChange(false)}
          />
        )}
      </DialogContent>
    </Dialog>
  )
}

interface PlayerHistoryContentProps {
  peladaId: number
  members: PeladaMember[]
  initialUserId: number | null
  onNavigate: () => void
}

function PlayerHistoryContent({
  peladaId,
  members,
  initialUserId,
  onNavigate,
}: PlayerHistoryContentProps) {
  const navigate = useNavigate()
  const [userId, setUserId] = useState<number | null>(initialUserId)
  const [period, setPeriod] = useState<Period>("5")
  const limit = period === "all" ? null : Number(period)
  // rows come from the backend already limited to the period, newest first
  const { rows, totalSessions, loading, fetching, error, retry } =
    usePlayerPeladaHistory(peladaId, userId, limit)

  const player = members.find((m) => m.id === userId) ?? null
  const summary = useMemo(() => summarize(rows), [rows])
  const chartData = useMemo(
    () =>
      [...rows]
        .reverse()
        .map((row) => ({ date: row.date, goals: row.goals, assists: row.assists })),
    [rows]
  )

  const openDaily = (dailyId: number) => {
    onNavigate()
    navigate(`/daily/${dailyId}`)
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Histórico do jogador</DialogTitle>
        <DialogDescription>
          Desempenho sessão a sessão nesta pelada.
        </DialogDescription>
      </DialogHeader>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Select
          value={userId?.toString() ?? ""}
          onValueChange={(value) => setUserId(Number(value))}
        >
          <SelectTrigger className="sm:flex-1" aria-label="Selecionar jogador">
            <SelectValue placeholder="Selecione um jogador..." />
          </SelectTrigger>
          <SelectContent>
            {members.map((member) => (
              <SelectItem key={member.id} value={member.id.toString()}>
                <span className="flex items-center gap-2">
                  <Avatar className="h-5 w-5">
                    {member.image && (
                      <AvatarImage src={getFileUrl(member.image)} alt="" />
                    )}
                    <AvatarFallback className="text-[10px]">
                      {getPeladaInitials(member.username)}
                    </AvatarFallback>
                  </Avatar>
                  {member.username}
                </span>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {player && (
          <Button variant="outline" size="sm" asChild>
            <Link to={`/profile/${player.id}`} onClick={onNavigate}>
              <ExternalLink className="h-4 w-4" />
              Ver perfil
            </Link>
          </Button>
        )}
      </div>

      {userId === null ? (
        <p className="py-8 text-center text-sm text-muted-foreground">
          Selecione um jogador para ver o histórico.
        </p>
      ) : loading ? (
        <HistorySkeleton />
      ) : error ? (
        <div className="flex flex-col items-center gap-3 py-8 text-center">
          <p className="text-sm text-muted-foreground">
            Não foi possível carregar o histórico.
          </p>
          <Button variant="outline" size="sm" onClick={retry}>
            Tentar novamente
          </Button>
        </div>
      ) : totalSessions === 0 && !fetching ? (
        <p className="py-8 text-center text-sm text-muted-foreground">
          Nenhuma sessão finalizada ainda.
        </p>
      ) : (
        <div
          className={cn("flex flex-col gap-4 transition-opacity", fetching && "opacity-60")}
          aria-busy={fetching}
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-sm text-muted-foreground">
              {rows.length === totalSessions
                ? totalSessions === 1
                  ? "1 sessão"
                  : `Todas as ${totalSessions} sessões`
                : `Últimas ${rows.length} de ${totalSessions} sessões`}
            </span>
            <Tabs value={period} onValueChange={(value) => setPeriod(value as Period)}>
              <TabsList className="h-9" aria-label="Período">
                {PERIODS.map((p) => (
                  <TabsTrigger key={p.value} value={p.value} className="px-2.5 text-xs">
                    {p.label}
                  </TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
          </div>

          {/* Totals of the selected period; Vitórias = partidas vencidas, Títulos = sessões vencidas */}
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-5">
            <SummaryTile label="Sessões" value={summary.sessions} />
            <SummaryTile label="Gols" value={summary.goals} />
            <SummaryTile label="Assistências" value={summary.assists} />
            <SummaryTile label="Vitórias" value={summary.matchWins} />
            <SummaryTile label="Títulos" value={summary.titles} />
          </div>

          {chartData.length >= 2 && (
            <section aria-label="Gols e assistências por sessão">
              <ChartContainer config={chartConfig} className="aspect-auto h-[180px] w-full">
                <LineChart data={chartData} margin={{ left: -20, right: 8, top: 8 }}>
                  <CartesianGrid vertical={false} strokeDasharray="3 3" className="stroke-border" />
                  <XAxis
                    dataKey="date"
                    tickLine={false}
                    axisLine={false}
                    tickMargin={8}
                    minTickGap={24}
                    tickFormatter={(value: string) => formatDay(value)}
                  />
                  <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={40} />
                  <ChartTooltip
                    content={
                      <ChartTooltipContent
                        labelFormatter={(value) => formatFullDate(String(value))}
                      />
                    }
                  />
                  <Line
                    dataKey="goals"
                    type="monotone"
                    stroke="var(--color-goals)"
                    strokeWidth={2}
                    dot={{ r: 3 }}
                  />
                  <Line
                    dataKey="assists"
                    type="monotone"
                    stroke="var(--color-assists)"
                    strokeWidth={2}
                    dot={{ r: 3 }}
                  />
                </LineChart>
              </ChartContainer>
            </section>
          )}

          <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="h-8 px-2 text-xs">Data</TableHead>
                  <TableHead className="h-8 px-2 text-center text-xs" title="Jogos">J</TableHead>
                  <TableHead className="h-8 px-2 text-center text-xs" title="Vitórias">V</TableHead>
                  <TableHead className="h-8 px-2 text-center text-xs" title="Gols">G</TableHead>
                  <TableHead className="h-8 px-2 text-center text-xs" title="Assistências">A</TableHead>
                  <TableHead className="h-8 px-2 text-xs">
                    <span className="sr-only">Resultado</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow
                    key={row.dailyId}
                    className="cursor-pointer"
                    onClick={() => openDaily(row.dailyId)}
                  >
                    <TableCell className="px-2 py-2">
                      <Link
                        to={`/daily/${row.dailyId}`}
                        onClick={(e) => {
                          e.stopPropagation()
                          onNavigate()
                        }}
                        className="font-medium hover:underline"
                      >
                        {formatDay(row.date)}
                      </Link>
                    </TableCell>
                    <TableCell className="px-2 py-2 text-center">{row.matchesPlayed}</TableCell>
                    <TableCell className="px-2 py-2 text-center">{row.wins}</TableCell>
                    <TableCell className="px-2 py-2 text-center">{row.goals}</TableCell>
                    <TableCell className="px-2 py-2 text-center">{row.assists}</TableCell>
                    <TableCell className="px-2 py-2 text-right">
                      {row.wonSession && (
                        <Badge variant="secondary" className="gap-1">
                          <Trophy className="h-3 w-3" />
                          Campeão
                        </Badge>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
          </Table>
        </div>
      )}
    </>
  )
}

function SummaryTile({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex flex-col items-center rounded-lg border bg-card px-2 py-3 text-center">
      <span className="text-xl font-bold tabular-nums">{value}</span>
      <span className="text-xs text-muted-foreground">{label}</span>
    </div>
  )
}

function HistorySkeleton() {
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-3 gap-2 sm:grid-cols-5">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-16 rounded-lg" />
        ))}
      </div>
      <Skeleton className="h-[180px] w-full" />
      <div className="space-y-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-8 w-full" />
        ))}
      </div>
    </div>
  )
}
