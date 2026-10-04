import { PieChart, Pie, Cell, Label } from 'recharts'
import { ChartContainer, ChartTooltip, ChartTooltipContent } from '@/components/ui/chart'
import type { ChartConfig } from '@/components/ui/chart'

const PIE_CONFIG: ChartConfig = {
  Goals: { label: 'Gols', color: 'hsl(var(--chart-1))' },
  Assists: { label: 'Assistências', color: 'hsl(var(--chart-2))' },
}

interface ProfilePieChartProps {
  goals: number
  assists: number
}

export default function ProfilePieChart({ goals, assists }: ProfilePieChartProps) {
  const pieData = [
    // fills come from the chart config, so the legend and the slices share one color source
    { name: 'Goals', value: goals, fill: 'var(--color-Goals)' },
    { name: 'Assists', value: assists, fill: 'var(--color-Assists)' },
  ]
  const totalContributions = goals + assists

  return (
    <div className="flex flex-col items-center gap-2 shrink-0">
      <ChartContainer config={PIE_CONFIG} className="h-[200px] w-[200px]">
        <PieChart>
          <Pie data={pieData} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius={55} outerRadius={75} strokeWidth={0}>
            {pieData.map((entry) => (
              <Cell key={entry.name} fill={entry.fill} />
            ))}
            <Label
              content={({ viewBox }) => {
                if (viewBox && 'cx' in viewBox && 'cy' in viewBox) {
                  return (
                    <text x={viewBox.cx} y={viewBox.cy} textAnchor="middle" dominantBaseline="middle">
                      <tspan
                        x={viewBox.cx}
                        y={viewBox.cy}
                        style={{ fontSize: '1.5rem', fontWeight: 700, fill: 'currentColor' }}
                      >
                        {totalContributions}
                      </tspan>
                      <tspan
                        x={viewBox.cx}
                        y={(viewBox.cy ?? 0) + 22}
                        style={{ fontSize: '0.7rem', fill: '#888' }}
                      >
                        Contribuições
                      </tspan>
                    </text>
                  )
                }
              }}
            />
          </Pie>
          <ChartTooltip content={<ChartTooltipContent nameKey="name" />} />
        </PieChart>
      </ChartContainer>
      <div className="flex gap-4 text-xs text-muted-foreground">
        <span className="flex items-center gap-1"><span className="inline-block h-2 w-2 rounded-sm bg-[#32CD32]" />Gols</span>
        <span className="flex items-center gap-1"><span className="inline-block h-2 w-2 rounded-sm bg-[#99CB0E]" />Assist.</span>
      </div>
      <div className="flex flex-col items-center gap-0.5 text-center">
        <span className="text-xs font-semibold">
          {goals} gols e {assists} assistências
        </span>
      </div>
    </div>
  )
}
