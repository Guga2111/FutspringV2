import type { DailyDetail } from '../../types/daily'
import { Button } from '../ui/button'

interface LiveSessionCardProps {
  daily: DailyDetail
  onEnterResults: () => void
}

export default function LiveSessionCard({ daily, onEnterResults }: LiveSessionCardProps) {
  if (daily.status !== 'IN_COURSE' || daily.matches.length > 0) return null

  return (
    <div className="mb-6">
      {daily.isAdmin ? (
        <div className="relative rounded-xl">
          <div className="absolute inset-0 rounded-xl border-2 border-green-500 animate-pulse pointer-events-none" />
          <div className="rounded-xl p-5 bg-green-50 dark:bg-green-950/20 flex flex-col sm:flex-row items-start sm:items-center gap-4">
            <div className="flex-1">
              <p className="font-semibold text-green-900 dark:text-green-100">Sessão está ao vivo — coloque resultados quando pronto</p>
              <p className="text-sm text-green-700 dark:text-green-300 mt-1">
                Quando a partida acabar, coloque os resultados e acabe a sessão.
              </p>
            </div>
            <Button variant="gradient" onClick={onEnterResults}>
              Preencha Resultados
            </Button>
          </div>
        </div>
      ) : (
        <div className="rounded-xl border border-green-500 p-4 bg-green-50 dark:bg-green-950/20 flex items-center gap-3">
          <span className="animate-pulse bg-green-500 rounded-full w-2.5 h-2.5 inline-block flex-shrink-0" />
          <p className="text-sm font-medium text-green-800 dark:text-green-200">Sessão ao vivo</p>
        </div>
      )}
    </div>
  )
}
