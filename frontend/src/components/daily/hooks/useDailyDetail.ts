import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { getDailyDetail } from '../../../api/dailies'
import type { DailyDetail } from '../../../types/daily'

export function useDailyDetail(id: number) {
  const [daily, setDaily] = useState<DailyDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const refetch = useCallback(() => {
    setLoading(true)
    getDailyDetail(id)
      .then((data) => {
        setDaily(data)
        setError(null)
        setAccessDenied(false)
      })
      .catch((err) => {
        const e = err as { response?: { status?: number } }
        if (e?.response?.status === 403) {
          setAccessDenied(true)
        } else {
          setError(err)
          toast.error('Falha ao carregar a sessão')
        }
      })
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    refetch()
  }, [refetch])

  const isAdmin = daily?.isAdmin ?? false

  const formattedDate =
    daily != null
      ? new Date(daily.dailyDate + 'T12:00:00').toLocaleDateString(undefined, {
          weekday: 'long',
          year: 'numeric',
          month: 'long',
          day: 'numeric',
        })
      : ''

  return { daily, setDaily, loading, accessDenied, error, isAdmin, formattedDate, refetch }
}
