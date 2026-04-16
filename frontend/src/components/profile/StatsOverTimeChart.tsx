import { useState, useMemo } from 'react'
import { Area, AreaChart, CartesianGrid, XAxis } from 'recharts'
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
} from '../ui/chart'
import type { ChartConfig } from '../ui/chart'
import { Card, CardContent, CardDescription, CardHeader } from '../ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../ui/select'
import { Skeleton } from '../ui/skeleton'
import type { TimelinePoint } from '../../types/stats'

interface StatsOverTimeChartProps {
  timelinePoints: TimelinePoint[]
  loading: boolean
}

const chartConfig = {
  goals:         { label: 'Goals',   color: '#32CD32' },
  assists:       { label: 'Assists', color: '#99CB0E' },
  matchesPlayed: { label: 'Matches', color: '#f97316' },
} satisfies ChartConfig

export default function StatsOverTimeChart({ timelinePoints = [], loading }: StatsOverTimeChartProps) {
  const [timeRange, setTimeRange] = useState('90d')

  const filteredData = useMemo(() => {
    const now = new Date()
    const days = timeRange === '7d' ? 7 : timeRange === '30d' ? 30 : 90
    const cutoff = new Date(now)
    cutoff.setDate(cutoff.getDate() - days)
    return timelinePoints.filter((row) => new Date(row.date) >= cutoff)
  }, [timelinePoints, timeRange])

  if (loading) {
    return (
      <Card className="pt-0 flex-1 min-w-0">
        <CardHeader className="flex items-center gap-2 border-b py-5 sm:flex-row">
          <div className="grid flex-1 gap-1">
            <Skeleton className="h-5 w-32" />
            <Skeleton className="h-4 w-48 mt-1" />
          </div>
          <Skeleton className="h-9 w-[160px]" />
        </CardHeader>
        <CardContent className="px-2 pt-4 sm:px-6 sm:pt-6">
          <Skeleton className="h-[250px] w-full" />
        </CardContent>
      </Card>
    )
  }

  if (timelinePoints.length === 0) {
    return (
      <Card className="pt-0 flex-1 min-w-0">
        <CardHeader className="flex items-center gap-2 border-b py-5 sm:flex-row">
          <div className="grid flex-1 gap-1">
            <CardDescription>Gols, Assists &amp; Partidas pelo tempo</CardDescription>
          </div>
        </CardHeader>
        <CardContent className="px-2 pt-4 sm:px-6 sm:pt-6">
          <p className="text-muted-foreground text-sm py-4">Sem histórico de partidas ainda.</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="pt-0 flex-1 min-w-0">
      <CardHeader className="flex items-center gap-2 border-b py-5 sm:flex-row">
        <div className="grid flex-1 gap-1">
          <CardDescription>Goals, Assists &amp; Matches over time</CardDescription>
        </div>
        <Select value={timeRange} onValueChange={setTimeRange}>
          <SelectTrigger className="hidden w-[160px] sm:flex">
            <SelectValue placeholder="Last 3 months" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="90d">Last 3 months</SelectItem>
            <SelectItem value="30d">Last 30 days</SelectItem>
            <SelectItem value="7d">Last 7 days</SelectItem>
          </SelectContent>
        </Select>
      </CardHeader>
      <CardContent className="px-2 pt-4 sm:px-6 sm:pt-6">
        <ChartContainer config={chartConfig} className="aspect-auto h-[250px] w-full">
          <AreaChart data={filteredData}>
            <defs>
              <linearGradient id="fillGoals" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#32CD32" stopOpacity={0.8} />
                <stop offset="95%" stopColor="#32CD32" stopOpacity={0.1} />
              </linearGradient>
              <linearGradient id="fillAssists" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#99CB0E" stopOpacity={0.8} />
                <stop offset="95%" stopColor="#99CB0E" stopOpacity={0.1} />
              </linearGradient>
              <linearGradient id="fillMatchesPlayed" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#f97316" stopOpacity={0.8} />
                <stop offset="95%" stopColor="#f97316" stopOpacity={0.1} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="currentColor" strokeOpacity={0.1} />
            <XAxis
              dataKey="date"
              tickLine={false}
              axisLine={false}
              tickMargin={8}
              minTickGap={32}
              tickFormatter={(value: string) => {
                const d = new Date(value)
                return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
              }}
            />
            <ChartTooltip
              content={
                <ChartTooltipContent
                  labelFormatter={(value) =>
                    new Date(String(value)).toLocaleDateString('en-US', {
                      month: 'short',
                      day: 'numeric',
                      year: 'numeric',
                    })
                  }
                />
              }
            />
            <Area
              dataKey="goals"
              type="natural"
              fill="url(#fillGoals)"
              stroke="#32CD32"
              legendType="circle"
            />
            <Area
              dataKey="assists"
              type="natural"
              fill="url(#fillAssists)"
              stroke="#99CB0E"
              legendType="circle"
            />
            <Area
              dataKey="matchesPlayed"
              type="natural"
              fill="url(#fillMatchesPlayed)"
              stroke="#f97316"
              legendType="circle"
            />
            <ChartLegend content={(props) => {
              const order = ['goals', 'assists', 'matchesPlayed']
              const sorted = [...(props.payload ?? [])].sort(
                (a, b) => order.indexOf(String(a.dataKey)) - order.indexOf(String(b.dataKey))
              )
              return <ChartLegendContent payload={sorted} verticalAlign={props.verticalAlign} />
            }} />
          </AreaChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}
