import { useCallback, useEffect, useState } from "react"
import { getErrorMessage, getErrorStatus } from "@/lib/errors"

interface Result<T> {
  fetcher: () => Promise<T>
  attempt: number
  data: T | null
  error: string | null
  notFound: boolean
}

// One read of the platform panel. `fetcher` must be stable (useCallback over its filters): the result is kept for that
// fetcher, so a newer filter never shows an older answer. notFound = the API's 404, which the panel answers to anyone
// who isn't a platform admin.
export function usePlatformRequest<T>(fetcher: () => Promise<T>, fallback: string) {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<Result<T> | null>(null)

  useEffect(() => {
    let cancelled = false
    fetcher()
      .then((data) => {
        if (!cancelled) setResult({ fetcher, attempt, data, error: null, notFound: false })
      })
      .catch((error) => {
        if (cancelled) return
        const notFound = getErrorStatus(error) === 404
        setResult({ fetcher, attempt, data: null, error: getErrorMessage(error, fallback), notFound })
      })
    return () => {
      cancelled = true
    }
  }, [fetcher, attempt, fallback])

  const current = result?.fetcher === fetcher && result.attempt === attempt ? result : null
  // a command's answer replaces the data on screen
  const setData = useCallback((update: (data: T) => T) => {
    setResult((prev) => (prev?.data ? { ...prev, data: update(prev.data) } : prev))
  }, [])

  return {
    data: current?.data ?? null,
    loading: current === null,
    error: current?.notFound ? null : (current?.error ?? null),
    notFound: current?.notFound ?? false,
    retry: () => setAttempt((value) => value + 1),
    setData,
  }
}

// The page of a list belongs to the filter it was picked under, so a new filter starts at page 0 without an effect
export function usePageOf(filterKey: string) {
  const [paging, setPaging] = useState({ filterKey, page: 0 })
  const page = paging.filterKey === filterKey ? paging.page : 0
  return [page, (next: number) => setPaging({ filterKey, page: next })] as const
}

// The value after `delay` ms without changes (the searches: 300 ms)
export function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}
