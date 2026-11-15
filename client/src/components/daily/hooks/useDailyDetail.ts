import { useCallback, useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import { getDailyDetail } from "@/api/dailies"
import { getErrorStatus } from "@/lib/errors"
import type { DailyDetail } from "@/types/daily"

export function useDailyDetail(id: number) {
  const [daily, setDaily] = useState<DailyDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const mounted = useRef(false)
  const request = useRef(0)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  // Reloads the detail without showing the skeleton again; returns the promise so callers can await it
  const refetch = useCallback(async () => {
    if (!mounted.current) return
    const generation = ++request.current
    const current = () => mounted.current && generation === request.current
    try {
      const data = await getDailyDetail(id)
      if (!current()) return
      setDaily(data)
      setError(null)
      setAccessDenied(false)
    } catch (err) {
      if (!current()) return
      if (getErrorStatus(err) === 403) {
        setDaily(null)
        setError(null)
        setAccessDenied(true)
      } else {
        setAccessDenied(false)
        setError(err)
        toast.error("Não foi possível carregar a sessão")
      }
    } finally {
      if (current()) setLoading(false)
    }
  }, [id])

  useEffect(() => {
    void refetch()
  }, [refetch])

  const isAdmin = daily?.isAdmin ?? false

  const formattedDate =
    daily != null
      ? new Date(daily.dailyDate + "T12:00:00").toLocaleDateString("pt-BR", {
          weekday: "long",
          year: "numeric",
          month: "long",
          day: "numeric",
        })
      : ""

  return { daily, setDaily, loading, accessDenied, error, isAdmin, formattedDate, refetch }
}
