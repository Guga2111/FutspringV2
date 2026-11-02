import { useMemo, useState } from "react"
import { format } from "date-fns"
import { ptBR } from "date-fns/locale"
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type { TimelinePoint } from "@/types/stats"
import { parseLocalDate } from "@/utils/dates"
import {
  filterTimeline,
  timelineRangeLabel,
  timelineTotals,
  type TimelineMetric,
  type TimelineRange,
} from "@/utils/profile"

interface EvolutionChartProps {
  timelinePoints: TimelinePoint[]
  loading: boolean
}

// goals carry the highlight across the app, assists the brand
const chartConfig = {
  goals: { label: "Gols", color: "hsl(var(--chart-2))" },
  assists: { label: "Assistências", color: "hsl(var(--chart-1))" },
} satisfies ChartConfig

const SERIES = ["goals", "assists"] as const satisfies readonly TimelineMetric[]

// Recharts animates in JavaScript, which the global reduced-motion CSS doesn't reach
const prefersReducedMotion =
  typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches
const RANGES: TimelineRange[] = ["7d", "30d", "90d"]

// Goals and assists per session date as two lines; the header's totals are the legend
export function EvolutionChart({ timelinePoints, loading }: EvolutionChartProps) {
  const [range, setRange] = useState<TimelineRange>("90d")

  const data = useMemo(() => filterTimeline(timelinePoints, range, new Date()), [timelinePoints, range])
  const totals = useMemo(() => timelineTotals(data), [data])

  return (
    <Card className="flex flex-col overflow-hidden">
      <CardHeader className="flex-row flex-wrap items-center justify-between gap-3 space-y-0 p-4 pb-3 md:p-5 md:pb-3">
        <CardTitle className="text-title font-semibold">Evolução</CardTitle>
        <ToggleGroup
          type="single"
          variant="chip"
          size="chip"
          value={range}
          onValueChange={(value) => value && setRange(value as TimelineRange)}
          aria-label="Período"
        >
          {RANGES.map((item) => (
            <ToggleGroupItem key={item} value={item}>
              {timelineRangeLabel[item]}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
      </CardHeader>

      <dl className="grid grid-cols-2 border-y">
        {SERIES.map((item) => (
          <div key={item} className="flex flex-col gap-0.5 px-4 py-2.5 md:px-5 md:py-3 [&:not(:first-child)]:border-l">
            <dt className="flex items-center gap-1.5 text-caption text-muted-foreground">
              <span aria-hidden className="size-2 rounded-full" style={{ backgroundColor: chartConfig[item].color }} />
              {chartConfig[item].label}
            </dt>
            <dd className="font-display text-title font-bold tabular-nums md:text-heading">{loading ? "–" : totals[item]}</dd>
          </div>
        ))}
      </dl>

      <CardContent className="flex-1 px-2 pb-4 pt-4 md:px-5 md:pb-5">
        {loading ? (
          <Skeleton className="h-[220px] w-full" />
        ) : data.length === 0 ? (
          <p className="flex h-[220px] items-center justify-center text-body text-muted-foreground">
            Sem partidas nos últimos {timelineRangeLabel[range]}.
          </p>
        ) : (
          <ChartContainer config={chartConfig} className="aspect-auto h-[220px] w-full">
            <LineChart data={data} margin={{ left: 0, right: 12, top: 8 }}>
              <CartesianGrid vertical={false} />
              <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={28} tickMargin={4} />
              <XAxis
                dataKey="date"
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                minTickGap={24}
                tickFormatter={(value: string) => format(parseLocalDate(value), "dd/MM")}
              />
              <ChartTooltip
                cursor={{ stroke: "hsl(var(--border))" }}
                content={
                  <ChartTooltipContent
                    labelFormatter={(value) => format(parseLocalDate(String(value)), "EEE, dd/MM/yyyy", { locale: ptBR })}
                  />
                }
              />
              {SERIES.map((item) => (
                <Line
                  key={item}
                  dataKey={item}
                  type="linear"
                  stroke={`var(--color-${item})`}
                  strokeWidth={2}
                  dot={{ r: 3, fill: `var(--color-${item})`, strokeWidth: 0 }}
                  activeDot={{ r: 5 }}
                  isAnimationActive={!prefersReducedMotion}
                />
              ))}
            </LineChart>
          </ChartContainer>
        )}
      </CardContent>
    </Card>
  )
}
