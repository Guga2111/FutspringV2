import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import type { MonthRevenue } from "@/types/platform"
import { formatFinanceMoney } from "@/utils/finance"
import { revenueChartData, type RevenuePoint } from "@/utils/platform"

const chartConfig = {
  value: { label: "Receita", color: "hsl(var(--chart-1))" },
} satisfies ChartConfig

// Recharts animates in JavaScript, which the global reduced-motion CSS doesn't reach
const prefersReducedMotion =
  typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches

// The last 12 months of payments (voided ones left out), the current month last
export function RevenueChart({ months }: { months: MonthRevenue[] }) {
  const data = revenueChartData(months)
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-title">Receita dos últimos 12 meses (R$)</CardTitle>
      </CardHeader>
      <CardContent>
        <ChartContainer config={chartConfig} className="aspect-auto h-[220px] w-full">
          <BarChart data={data} margin={{ left: 0, right: 4, top: 8 }}>
            <CartesianGrid vertical={false} />
            <YAxis tickLine={false} axisLine={false} width={36} tickMargin={4} />
            <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} minTickGap={8} />
            <ChartTooltip
              cursor={{ fill: "hsl(var(--row-hover))" }}
              content={
<ChartTooltipContent formatValue={(item) => formatFinanceMoney((item.payload as RevenuePoint).total)} />
              }
            />
            <Bar dataKey="value" fill="var(--color-value)" radius={[4, 4, 0, 0]} isAnimationActive={!prefersReducedMotion} />
          </BarChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}
