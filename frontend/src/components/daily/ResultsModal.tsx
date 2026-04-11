import { useState } from 'react'
import { toast } from 'sonner'
import type { DailyDetail } from '../../types/daily'
import { submitResults } from '../../api/dailies'
import type { MatchResultInput } from '../../api/dailies'
import { Button } from '../ui/button'

interface MatchFormRow {
  matchId: number | null
  team1Id: number
  team2Id: number
  team1Score: number
  team2Score: number
  statsExpanded: boolean
  playerStats: { userId: number; username: string; goals: number; assists: number }[]
}

function buildPlayerStats(
  daily: DailyDetail,
  team1Id: number,
  team2Id: number,
  existing: { userId: number; goals: number; assists: number }[] = [],
) {
  const statMap = new Map(existing.map((s) => [s.userId, s]))
  const teamPlayers = daily.teams
    .filter((t) => t.id === team1Id || t.id === team2Id)
    .flatMap((t) => t.players)
  return teamPlayers.map((p) => {
    const s = statMap.get(p.id)
    return { userId: p.id, username: p.username, goals: s?.goals ?? 0, assists: s?.assists ?? 0 }
  })
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

interface ResultsModalProps {
  daily: DailyDetail
  onClose: () => void
  onSuccess: (updated: DailyDetail) => void
}

export default function ResultsModal({ daily, onClose, onSuccess }: ResultsModalProps) {
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
      // Re-fetch fresh detail to get updated matches
      const { getDailyDetail } = await import('../../api/dailies')
      const updated = await getDailyDetail(daily.id)
      toast.success('Resultados salvados!')
      onSuccess(updated)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao salvar resultados')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 overflow-y-auto py-8 px-4">
      <div className="bg-background rounded-lg shadow-lg w-full max-w-2xl">
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-lg font-semibold">Coloque o resultados das partidas</h2>
          <button
            aria-label="Close"
            className="text-muted-foreground hover:text-foreground text-xl leading-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-full"
            onClick={onClose}
          >
            ×
          </button>
        </div>
        <div className="p-4 space-y-6">
          {rows.length === 0 && (
            <p className="text-sm text-muted-foreground">
              Sem times disponíveis. Sorteie os times antes dos resultados.
            </p>
          )}
          {rows.map((row, mi) => {
            const team1Name =
              daily.teams.find((t) => t.id === row.team1Id)?.name ?? `Team ${row.team1Id}`
            const team2Name =
              daily.teams.find((t) => t.id === row.team2Id)?.name ?? `Team ${row.team2Id}`
            return (
              <div key={mi} className="border rounded-lg p-4">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="font-medium text-sm">Partida {mi + 1}</h3>
                  {rows.length > 1 && (
                    <button
                      className="text-xs text-destructive hover:underline"
                      onClick={() => removeMatch(mi)}
                    >
                      Remova
                    </button>
                  )}
                </div>

                {/* Team selects */}
                <div className="grid grid-cols-2 gap-3 mb-1">
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">Time 1</label>
                    <select
                      className="w-full text-sm border rounded px-2 py-1.5 bg-background"
                      value={row.team1Id}
                      onChange={(e) => {
                        const newId = Number(e.target.value)
                        updateRow(mi, { team1Id: newId, playerStats: buildPlayerStats(daily, newId, row.team2Id) })
                      }}
                    >
                      {daily.teams.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">Time 2</label>
                    <select
                      className="w-full text-sm border rounded px-2 py-1.5 bg-background"
                      value={row.team2Id}
                      onChange={(e) => {
                        const newId = Number(e.target.value)
                        updateRow(mi, { team2Id: newId, playerStats: buildPlayerStats(daily, row.team1Id, newId) })
                      }}
                    >
                      {daily.teams.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {/* Score steppers */}
                <div className="flex items-center justify-center gap-4 mb-3 mt-3">
                  <div className="flex flex-col items-center gap-1">
                    <div className="flex items-center gap-2">
                      <Button variant="outline" size="sm" className="h-9 w-9 p-0 text-base" onClick={() => updateScore(mi, 'team1Score', -1)}>−</Button>
                      <span className="text-xl font-semibold w-8 text-center">{row.team1Score}</span>
                      <Button variant="outline" size="sm" className="h-9 w-9 p-0 text-base" onClick={() => updateScore(mi, 'team1Score', 1)}>+</Button>
                    </div>
                  </div>
                  <span className="text-muted-foreground font-medium">vs</span>
                  <div className="flex flex-col items-center gap-1">
                    <div className="flex items-center gap-2">
                      <Button variant="outline" size="sm" className="h-9 w-9 p-0 text-base" onClick={() => updateScore(mi, 'team2Score', -1)}>−</Button>
                      <span className="text-xl font-semibold w-8 text-center">{row.team2Score}</span>
                      <Button variant="outline" size="sm" className="h-9 w-9 p-0 text-base" onClick={() => updateScore(mi, 'team2Score', 1)}>+</Button>
                    </div>
                  </div>
                </div>

                {/* Player stats toggle */}
                <button
                  className="text-xs text-muted-foreground hover:text-foreground mb-2"
                  onClick={() => updateRow(mi, { statsExpanded: !row.statsExpanded })}
                >
                  {row.statsExpanded ? '▾ Esconda' : '▸ Mostre'} estatísticas dos jogadores (
                  {row.playerStats.length} jogadores)
                </button>

                {row.statsExpanded && (() => {
                  const team1PlayerIds = new Set(
                    daily.teams.find((t) => t.id === row.team1Id)?.players.map((p) => p.id) ?? []
                  )
                  const team1Stats = row.playerStats.filter((ps) => team1PlayerIds.has(ps.userId))
                  const team2Stats = row.playerStats.filter((ps) => !team1PlayerIds.has(ps.userId))

                  const renderPlayerStat = (ps: typeof row.playerStats[0]) => (
                    <div key={ps.userId} className="rounded-md border px-3 py-2 space-y-1.5">
                      <span className="text-sm font-medium">{ps.username}</span>
                      <div className="flex items-center gap-3 flex-wrap">
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground w-12">Gols</span>
                          <Button variant="outline" size="sm" className="h-7 w-7 p-0 text-base" onClick={() => updateStat(mi, ps.userId, 'goals', -1)}>−</Button>
                          <span className="w-6 text-center text-sm font-medium">{ps.goals}</span>
                          <Button variant="outline" size="sm" className="h-7 w-7 p-0 text-base" onClick={() => updateStat(mi, ps.userId, 'goals', 1)}>+</Button>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground w-12">Assists</span>
                          <Button variant="outline" size="sm" className="h-7 w-7 p-0 text-base" onClick={() => updateStat(mi, ps.userId, 'assists', -1)}>−</Button>
                          <span className="w-6 text-center text-sm font-medium">{ps.assists}</span>
                          <Button variant="outline" size="sm" className="h-7 w-7 p-0 text-base" onClick={() => updateStat(mi, ps.userId, 'assists', 1)}>+</Button>
                        </div>
                      </div>
                    </div>
                  )

                  return (
                    <div className="space-y-4 pt-1">
                      {team1Stats.length > 0 && (
                        <div className="space-y-2">
                          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">{team1Name}</p>
                          {team1Stats.map(renderPlayerStat)}
                        </div>
                      )}
                      {team2Stats.length > 0 && (
                        <div className="space-y-2">
                          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">{team2Name}</p>
                          {team2Stats.map(renderPlayerStat)}
                        </div>
                      )}
                    </div>
                  )
                })()}
              </div>
            )
          })}

          {daily.teams.length >= 2 && (
            <button
              className="text-sm text-muted-foreground border border-dashed rounded-full px-3 py-2 w-full hover:bg-muted"
              onClick={addMatch}
            >
              + Adicione Partida
            </button>
          )}
        </div>

        <div className="flex justify-end gap-3 p-4 border-t">
          <Button
            className="text-sm px-4 py-2 rounded-full border disabled:opacity-50"
            disabled={loading}
            onClick={onClose}
            variant="outline"
          >
            Cancelar
          </Button>
          <Button
            className="text-sm bg-gradient-primary text-white px-4 py-2 rounded-full disabled:opacity-50"
            disabled={loading || rows.length === 0}
            onClick={handleSubmit}
          >
            {loading ? 'Salvando...' : 'Salvar Resultados'}
          </Button>
        </div>
      </div>
    </div>
  )
}
