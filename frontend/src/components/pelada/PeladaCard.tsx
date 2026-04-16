import { Link } from 'react-router-dom'
import { Card, CardContent } from '../ui/card'
import { Badge } from '../ui/badge'
import { getPeladaInitials, getPeladaGradient, getFileUrl } from '../../lib/utils'
import type { PeladaResponse } from '../../types/pelada'
import type { DailyListItem } from '../../types/daily'

function formatDateBR(dateStr: string): string {
  const [year, month, day] = dateStr.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return date.toLocaleDateString('pt-BR', { day: 'numeric', month: 'long', year: 'numeric', weekday: 'long' })
}

export function getNextSession(dailies: DailyListItem[]): string | null {
  const upcoming = dailies
    .filter((d) => d.status === 'SCHEDULED' || d.status === 'CONFIRMED')
    .sort((a, b) => a.dailyDate.localeCompare(b.dailyDate))
  return upcoming.length > 0 ? upcoming[0].dailyDate : null
}

export function PeladaCard({ pelada, nextSession }: { pelada: PeladaResponse; nextSession: string | null | undefined }) {
  return (
    <Link to={`/pelada/${pelada.id}`} className="block hover:opacity-90 transition-opacity">
      <Card className="overflow-hidden rounded-xl border shadow-sm">
        {pelada.image ? (
          <img
            src={getFileUrl(pelada.image)}
            alt={pelada.name}
            className="h-40 w-full object-cover"
          />
        ) : (
          <div className={`h-40 ${getPeladaGradient(pelada.name)} flex items-center justify-center`}>
            <span className="text-4xl font-extrabold text-white tracking-wide select-none">
              {getPeladaInitials(pelada.name)}
            </span>
          </div>
        )}
        <CardContent className="p-4">
          <h2 className="font-bold text-lg leading-tight">{pelada.name}</h2>
          <p className="text-sm text-muted-foreground mt-1">
            {pelada.dayOfWeek} · {pelada.timeOfDay}
          </p>
          <div className="flex items-center gap-2 mt-2">
            <Badge variant="secondary">{pelada.memberCount} membros</Badge>
            <span className="text-xs text-muted-foreground">
              {nextSession === undefined
                ? '…'
                : nextSession
                ? formatDateBR(nextSession)
                : 'Sem sessão definida'}
            </span>
          </div>
        </CardContent>
      </Card>
    </Link>
  )
}
