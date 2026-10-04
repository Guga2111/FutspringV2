import { useState } from 'react'
import { toast } from 'sonner'
import type { DailyDetail } from '../../../types/daily'
import { submitResults, getDailyDetail } from '../../../api/dailies'
import type { MatchResultInput } from '../../../api/dailies'
import { buildPlayerStats } from '../../../utils/matchStats'

export interface MatchFormRow {
  matchId: number | null
  team1Id: number
  team2Id: number
  team1Score: number
  team2Score: number
  statsExpanded: boolean
  playerStats: { userId: number; username: string; goals: number; assists: number }[]
}

function initMatchRows(daily: DailyDetail): MatchFormRow[] {
  if (daily.matches.length > 0) {
    return daily.matches.map((m) => ({
      matchId: m.id,
      team1Id: m.team1Id,
      team2Id: m.team2Id,
      team1Score: m.team1Score ?? 0,
      team2Score: m.team2Score ?? 0,
      statsExpanded: false,
      playerStats: buildPlayerStats(daily, m.team1Id, m.team2Id, m.playerStats),
    }))
  }
  if (daily.teams.length >= 2) {
    const t1 = daily.teams[0].id
    const t2 = daily.teams[1].id
    return [
      {
        matchId: null,
        team1Id: t1,
        team2Id: t2,
        team1Score: 0,
        team2Score: 0,
        statsExpanded: false,
        playerStats: buildPlayerStats(daily, t1, t2),
      },
    ]
  }
  return []
}

export function useMatchResults(daily: DailyDetail, onSubmit: (updated: DailyDetail) => void) {
  const [rows, setRows] = useState<MatchFormRow[]>(() => initMatchRows(daily))
  const [loading, setLoading] = useState(false)

  function updateRow(index: number, patch: Partial<MatchFormRow>) {
    setRows((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)))
  }

  function updateScore(rowIndex: number, field: 'team1Score' | 'team2Score', delta: number) {
    setRows((prev) =>
      prev.map((r, i) =>
        i !== rowIndex ? r : { ...r, [field]: Math.max(0, r[field] + delta) },
      ),
    )
  }

  function updateStat(rowIndex: number, userId: number, field: 'goals' | 'assists', delta: number) {
    setRows((prev) =>
      prev.map((r, i) => {
        if (i !== rowIndex) return r
        return {
          ...r,
          playerStats: r.playerStats.map((s) =>
            s.userId === userId ? { ...s, [field]: Math.max(0, s[field] + delta) } : s,
          ),
        }
      }),
    )
  }

  function addMatch() {
    const t1 = daily.teams[0]?.id ?? 0
    const t2 = daily.teams[1]?.id ?? 0
    setRows((prev) => [
      ...prev,
      {
        matchId: null,
        team1Id: t1,
        team2Id: t2,
        team1Score: 0,
        team2Score: 0,
        statsExpanded: false,
        playerStats: buildPlayerStats(daily, t1, t2),
      },
    ])
  }

  function removeMatch(index: number) {
    setRows((prev) => prev.filter((_, i) => i !== index))
  }

  async function handleSubmit() {
    setLoading(true)
    try {
      const payload: MatchResultInput[] = rows.map((r) => ({
        matchId: r.matchId ?? null,
        team1Id: r.team1Id,
        team2Id: r.team2Id,
        team1Score: r.team1Score,
        team2Score: r.team2Score,
        playerStats: r.playerStats
          .filter((ps) => ps.goals > 0 || ps.assists > 0)
          .map((ps) => ({
            userId: ps.userId,
            goals: ps.goals,
            assists: ps.assists,
          })),
      }))
      await submitResults(daily.id, payload)
      const updated = await getDailyDetail(daily.id)
      toast.success('Resultados salvados!')
      onSubmit(updated)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao salvar resultados')
    } finally {
      setLoading(false)
    }
  }

  return {
    rows,
    loading,
    updateRow,
    updateScore,
    updateStat,
    addMatch,
    removeMatch,
    handleSubmit,
  }
}
