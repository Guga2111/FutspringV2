import type { ReactNode } from 'react'
import { Card, CardContent } from '../ui/card'

interface KpiCardProps {
  label: string
  value: string | number
  icon?: ReactNode
}

export default function KpiCard({ label, value, icon }: KpiCardProps) {
  return (
    <Card className="border-t-2 border-t-green-500">
      <CardContent className="flex flex-col items-center justify-center text-center px-3 py-4 gap-1">
        {icon && <div className="mb-1">{icon}</div>}
        <span className="text-2xl font-bold">{value}</span>
        <span className="text-xs text-muted-foreground">{label}</span>
      </CardContent>
    </Card>
  )
}
