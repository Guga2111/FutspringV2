import { useEffect, useState } from "react"
import { searchUsers } from "@/api/peladas"
import type { PublicUser } from "@/types/user"

export const MIN_SEARCH_LENGTH = 3
const DEBOUNCE_MS = 300

interface SearchResult {
  query: string
  users: PublicUser[]
  failed: boolean
}

// Debounced user search (the API needs at least 3 characters). Results are keyed by query,
// so a slow response never replaces the results of a newer query.
export function useUserSearch(query: string) {
  const trimmed = query.trim()
  const enabled = trimmed.length >= MIN_SEARCH_LENGTH
  const [result, setResult] = useState<SearchResult | null>(null)

  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    const timer = setTimeout(() => {
      searchUsers(trimmed)
        .then((users) => !cancelled && setResult({ query: trimmed, users, failed: false }))
        .catch(() => !cancelled && setResult({ query: trimmed, users: [], failed: true }))
    }, DEBOUNCE_MS)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [trimmed, enabled])

  const current = enabled && result?.query === trimmed ? result : null
  return {
    users: current?.users ?? [],
    searching: enabled && current === null,
    failed: current?.failed ?? false,
    tooShort: trimmed.length > 0 && !enabled,
  }
}
