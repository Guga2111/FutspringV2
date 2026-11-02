import { Label, PolarAngleAxis, PolarRadiusAxis, RadialBar, RadialBarChart } from "recharts"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, type ChartConfig } from "@/components/ui/chart"
import { Progress } from "@/components/ui/progress"
import type { StatsDTO } from "@/types/stats"
import { formatAverage, profileRates } from "@/utils/profile"

interface PerformanceCardProps {
  stats: StatsDTO
}

const chartConfig = {
  winPct: { label: "Vitórias", color: "hsl(var(--chart-1))" },
} satisfies ChartConfig

// Match win rate as a thin ring (the card's lead figure), titles per session as a bar, then the averages
export function PerformanceCard({ stats }: PerformanceCardProps) {
  const rates = profileRates(stats)
  const matches = stats.matchesPlayed ?? 0
  const matchWins = stats.matchWins ?? 0
  const sessions = stats.sessionsPlayed ?? 0
  const averages = [
    { label: "Gols por sessão", value: rates.goalsPerSession },
    { label: "Assistências por sessão", value: rates.assistsPerSession },
    { label: "Participações em gol por sessão", value: rates.contributionsPerSession },
  ]

  return (
    <Card className="flex flex-col">
      <CardHeader className="p-4 pb-2 md:p-5 md:pb-2">
        <CardTitle className="text-title font-semibold">Rendimento</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-5 p-4 pt-0 md:p-5 md:pt-0">
        <div className="flex items-center gap-4">
          <ChartContainer config={chartConfig} className="aspect-square h-[132px] shrink-0">
            <RadialBarChart
              data={[{ key: "winPct", value: rates.winPct, fill: "var(--color-winPct)" }]}
              startAngle={90}
              endAngle={-270}
              innerRadius={54}
              outerRadius={66}
              barSize={9}
            >
              <PolarAngleAxis type="number" domain={[0, 100]} tick={false} axisLine={false} />
              <RadialBar dataKey="value" background={{ fill: "hsl(var(--muted))" }} cornerRadius={9} isAnimationActive={false} />
              <PolarRadiusAxis tick={false} tickLine={false} axisLine={false}>
                <Label
                  content={({ viewBox }) => {
                    if (!viewBox || !("cx" in viewBox) || !("cy" in viewBox)) return null
                    return (
                      <text x={viewBox.cx} y={viewBox.cy} textAnchor="middle" dominantBaseline="middle">
                        <tspan x={viewBox.cx} y={viewBox.cy} className="fill-foreground font-display text-score font-bold">
                          {rates.winPct}%
                        </tspan>
                      </text>
                    )
                  }}
                />
              </PolarRadiusAxis>
            </RadialBarChart>
          </ChartContainer>
          <div className="flex min-w-0 flex-col gap-1">
            <p className="text-body font-medium">Vitórias em partidas</p>
            <p className="text-body-sm text-muted-foreground tabular-nums">
              {matchWins} de {matches} {matches === 1 ? "partida" : "partidas"}
            </p>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <div className="flex items-baseline justify-between gap-3">
            <p className="text-body font-medium">Títulos</p>
            <p className="font-display text-title font-bold tabular-nums text-highlight-text">{rates.titlePct}%</p>
          </div>
          <Progress
            value={rates.titlePct}
            aria-label="Sessões vencidas"
            className="h-2"
            indicatorClassName="bg-highlight"
          />
          <p className="text-caption text-muted-foreground tabular-nums">
            {stats.wins} de {sessions} {sessions === 1 ? "sessão" : "sessões"}
          </p>
        </div>

        <dl className="flex flex-col divide-y">
          {averages.map((item) => (
            <div key={item.label} className="flex items-baseline justify-between gap-3 py-2 first:pt-0 last:pb-0">
              <dt className="text-body-sm text-muted-foreground">{item.label}</dt>
              <dd className="text-body font-semibold tabular-nums">{formatAverage(item.value)}</dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  )
}
