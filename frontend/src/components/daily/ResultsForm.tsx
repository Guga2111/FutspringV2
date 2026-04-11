import type { DailyDetail } from '../../types/daily'
import { Button } from '../ui/button'
import { buildPlayerStats } from '../../utils/matchStats'
import type { MatchFormRow } from './hooks/useMatchResults'

interface ResultsFormProps {
  daily: DailyDetail
  rows: MatchFormRow[]
  loading: boolean
  onClose: () => void
  onUpdateRow: (index: number, patch: Partial<MatchFormRow>) => void
  onUpdateScore: (rowIndex: number, field: 'team1Score' | 'team2Score', delta: number) => void
  onUpdateStat: (rowIndex: number, userId: number, field: 'goals' | 'assists', delta: number) => void
  onAddMatch: () => void
  onRemoveMatch: (index: number) => void
  onSubmit: () => void
}

export function ResultsForm({
  daily,
  rows,
  loading,
  onClose,
  onUpdateRow,
  onUpdateScore,
  onUpdateStat,
  onAddMatch,
  onRemoveMatch,
  onSubmit,
}: ResultsFormProps) {
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
                      onClick={() => onRemoveMatch(mi)}
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
                        onUpdateRow(mi, { team1Id: newId, playerStats: buildPlayerStats(daily, newId, row.team2Id) })
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
                        onUpdateRow(mi, { team2Id: newId, playerStats: buildPlayerStats(daily, row.team1Id, newId) })
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
                      <Button variant="outline" size="sm" className="h-9 w-9 p-0 text-base" onClick={() => onUpdateScore(mi, 'team1Score', -1)}>−</Button>
                      <span className="text-xl font-semibold w-8 text-center">{row.team1Score}</span>
                      <Button variant="outline" size="sm" className="h-9 w-9 p-0 text-base" onClick={() => onUpdateScore(mi, 'team1Score', 1)}>+</Button>
                    </div>
                  </div>
                  <span className="text-muted-foreground font-medium">vs</span>
                  <div className="flex flex-col items-center gap-1">
                    <div className="flex items-center gap-2">
                      <Button variant="outline" size="sm" className="h-9 w-9 p-0 text-base" onClick={() => onUpdateScore(mi, 'team2Score', -1)}>−</Button>
                      <span className="text-xl font-semibold w-8 text-center">{row.team2Score}</span>
                      <Button variant="outline" size="sm" className="h-9 w-9 p-0 text-base" onClick={() => onUpdateScore(mi, 'team2Score', 1)}>+</Button>
                    </div>
                  </div>
                </div>

                {/* Player stats toggle */}
                <button
                  className="text-xs text-muted-foreground hover:text-foreground mb-2"
                  onClick={() => onUpdateRow(mi, { statsExpanded: !row.statsExpanded })}
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
                          <Button variant="outline" size="sm" className="h-7 w-7 p-0 text-base" onClick={() => onUpdateStat(mi, ps.userId, 'goals', -1)}>−</Button>
                          <span className="w-6 text-center text-sm font-medium">{ps.goals}</span>
                          <Button variant="outline" size="sm" className="h-7 w-7 p-0 text-base" onClick={() => onUpdateStat(mi, ps.userId, 'goals', 1)}>+</Button>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground w-12">Assists</span>
                          <Button variant="outline" size="sm" className="h-7 w-7 p-0 text-base" onClick={() => onUpdateStat(mi, ps.userId, 'assists', -1)}>−</Button>
                          <span className="w-6 text-center text-sm font-medium">{ps.assists}</span>
                          <Button variant="outline" size="sm" className="h-7 w-7 p-0 text-base" onClick={() => onUpdateStat(mi, ps.userId, 'assists', 1)}>+</Button>
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
              onClick={onAddMatch}
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
            onClick={onSubmit}
          >
            {loading ? 'Salvando...' : 'Salvar Resultados'}
          </Button>
        </div>
      </div>
    </div>
  )
}
