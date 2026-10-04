import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { getDailyDetail } from '@/api/dailies'
import { getErrorStatus } from '@/lib/errors'
import type { DailyDetail } from '@/types/daily'

export function useDailyDetail(id: number) {
  const [daily, setDaily] = useState<DailyDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)
  const [error, setError] = useState<unknown>(null)

  // Reloads the detail without showing the skeleton again; returns the promise so callers can await it
  const refetch = useCallback(async () => {
    try {
      const data = await getDailyDetail(id)
      setDaily(data)
      setError(null)
      setAccessDenied(false)
    } catch (err) {
      if (getErrorStatus(err) === 403) {
        setAccessDenied(true)
      } else {
        setError(err)
        toast.error('Não foi possível carregar a sessão')
      }
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    void refetch()
  }, [refetch])

  const isAdmin = daily?.isAdmin ?? false

  const formattedDate =
    daily != null
      ? new Date(daily.dailyDate + 'T12:00:00').toLocaleDateString('pt-BR', {
          weekday: 'long',
          year: 'numeric',
          month: 'long',
          day: 'numeric',
        })
      : ''

  return { daily, setDaily, loading, accessDenied, error, isAdmin, formattedDate, refetch }
}
