import { useState, useMemo } from 'react'
import { CheckCircle2, AlertTriangle, X, Pencil } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '../ui/button'
import { parseSessionMessage } from '../../utils/parseSessionMessage'
import { autoMatchPlayers } from '../../utils/matchPlayers'
import { populateFromMessage } from '../../api/dailies'
import type { DailyDetail } from '../../types/daily'
import type { PopulateDailyInput } from '../../api/dailies'

interface MatchedPlayerState {
  rawName: string
  totalGoals: number
  totalAssists: number
  matchedUserId: number | null
  ambiguous: boolean
  skipped: boolean
}

interface ParsedTeamMeta {
  colorName: string
  colorHex: string
}

interface ParsedMatchMeta {
  team1ColorName: string
  team1Score: number
  team2ColorName: string
  team2Score: number
}

interface Props {
  daily: DailyDetail
  onClose: () => void
  onSuccess: (updated: DailyDetail) => void
}

export default function ImportFromMessageModal({ daily, onClose, onSuccess }: Props) {
  const [step, setStep] = useState<1 | 2>(1)
  const [text, setText] = useState('')
  const [parseError, setParseError] = useState<string | null>(null)
  const [parsedTeams, setParsedTeams] = useState<ParsedTeamMeta[]>([])
  const [parsedMatches, setParsedMatches] = useState<ParsedMatchMeta[]>([])
  const [matchedPlayers, setMatchedPlayers] = useState<MatchedPlayerState[][]>([])
  const [loading, setLoading] = useState(false)

  const members = daily.peladaMembers ?? []

  const handleAnalyze = () => {
    setParseError(null)
    const parsed = parseSessionMessage(text)
    if (parsed.teams.length === 0) {
      setParseError('Nenhum time encontrado. Verifique o formato da mensagem.')
      return
    }
    const autoMatched = autoMatchPlayers(parsed, members)
    const initialState: MatchedPlayerState[][] = autoMatched.map(teamPlayers =>
      teamPlayers.map(p => ({ ...p, skipped: false }))
    )

    setParsedTeams(parsed.teams.map(t => ({ colorName: t.colorName, colorHex: t.colorHex })))
    setParsedMatches(parsed.matches)
    setMatchedPlayers(initialState)
    setStep(2)
  }

  const assignedUserIds = useMemo(() => {
    const ids = new Set<number>()
    for (const teamPlayers of matchedPlayers) {
      for (const p of teamPlayers) {
        if (p.matchedUserId !== null) ids.add(p.matchedUserId)
      }
    }
    return ids
  }, [matchedPlayers])

  const handlePlayerChange = (
    teamIdx: number,
    playerIdx: number,
    userId: number | null,
    skipped: boolean,
  ) => {
    setMatchedPlayers(prev => {
      const next = prev.map(team => [...team])
      next[teamIdx] = [...next[teamIdx]]
      next[teamIdx][playerIdx] = { ...next[teamIdx][playerIdx], matchedUserId: userId, skipped }
      return next
    })
  }

  const allResolved = matchedPlayers.every(team =>
    team.every(p => p.matchedUserId !== null || p.skipped)
  )

  const handleImport = async () => {
    setLoading(true)
    try {
      const input: PopulateDailyInput = {
        teams: parsedTeams.map((team, i) => ({
          colorName: team.colorName,
          colorHex: team.colorHex,
          players: (matchedPlayers[i] ?? [])
            .filter(p => p.matchedUserId !== null && !p.skipped)
            .map(p => ({
              userId: p.matchedUserId!,
              totalGoals: p.totalGoals,
              totalAssists: p.totalAssists,
            })),
        })),
        matches: parsedMatches,
      }
      const updated = await populateFromMessage(daily.id, input)
      toast.success('Sessão importada com sucesso!')
      onSuccess(updated)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao importar sessão')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
      <div className="bg-background rounded-xl border border-border shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <div>
            <h2 className="text-base font-semibold">Importar da Mensagem</h2>
            <p className="text-xs text-muted-foreground mt-0.5">
              {step === 1 ? 'Passo 1 de 2 — Colar mensagem' : 'Passo 2 de 2 — Confirmar jogadores'}
            </p>
          </div>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground p-1">
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Body */}
        <div className="overflow-y-auto flex-1 px-5 py-4">
          {step === 1 ? (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">
                Cole a mensagem do WhatsApp com os times, jogadores e resultados abaixo.
              </p>
              <textarea
                className="w-full h-64 rounded-lg border border-border bg-muted/30 p-3 text-sm font-mono resize-none focus:outline-none focus:ring-2 focus:ring-ring"
                placeholder={"Azul 🔵\n\nLeal⚽️⚽️⚽️🅰️\nSouto ⚽️🅰️\nFerraz\n\nBranco ⚪️\n\nPedrão ⚽️\n\nAzul 2 x 0 Branco"}
                value={text}
                onChange={e => setText(e.target.value)}
              />
              {parseError && (
                <p className="text-sm text-destructive flex items-center gap-1.5">
                  <AlertTriangle className="h-4 w-4 flex-shrink-0" />
                  {parseError}
                </p>
              )}
            </div>
          ) : (
            <div className="space-y-6">
              {/* Teams */}
              {parsedTeams.map((team, teamIdx) => (
                <div key={teamIdx}>
                  <div className="flex items-center gap-2 mb-2">
                    <span
                      className="inline-block w-3 h-3 rounded-full border border-border flex-shrink-0"
                      style={{ backgroundColor: team.colorHex }}
                    />
                    <span className="text-sm font-semibold">{team.colorName}</span>
                  </div>
                  <div className="space-y-1.5 pl-5">
                    {(matchedPlayers[teamIdx] ?? []).map((player, playerIdx) => (
                      <div key={playerIdx} className="flex items-center gap-2 text-sm min-h-[28px]">
                        <span className="text-muted-foreground w-24 truncate shrink-0">{player.rawName}</span>
                        <span className="text-xs text-muted-foreground w-16 shrink-0">
                          {player.totalGoals > 0 && `⚽${player.totalGoals}`}
                          {player.totalGoals > 0 && player.totalAssists > 0 && ' '}
                          {player.totalAssists > 0 && `🅰️${player.totalAssists}`}
                        </span>
                        {player.matchedUserId !== null ? (
                          <span className="flex items-center gap-1.5 text-green-600 dark:text-green-400 text-xs">
                            <CheckCircle2 className="h-3.5 w-3.5 flex-shrink-0" />
                            {members.find(m => m.id === player.matchedUserId)?.username}
                            <button
                              className="text-muted-foreground hover:text-foreground ml-0.5"
                              onClick={() => handlePlayerChange(teamIdx, playerIdx, null, false)}
                              title="Trocar jogador"
                            >
                              <Pencil className="h-3 w-3" />
                            </button>
                          </span>
                        ) : player.skipped ? (
                          <span className="flex items-center gap-1.5 text-muted-foreground text-xs">
                            Pulado
                            <button
                              className="underline"
                              onClick={() => handlePlayerChange(teamIdx, playerIdx, null, false)}
                            >
                              Desfazer
                            </button>
                          </span>
                        ) : (
                          <span className="flex items-center gap-1.5">
                            <AlertTriangle className="h-3.5 w-3.5 text-orange-500 flex-shrink-0" />
                            <select
                              className="text-xs rounded border border-border bg-background px-1.5 py-0.5"
                              value=""
                              onChange={e => {
                                const val = e.target.value
                                if (val === '__skip__') {
                                  handlePlayerChange(teamIdx, playerIdx, null, true)
                                } else if (val) {
                                  handlePlayerChange(teamIdx, playerIdx, Number(val), false)
                                }
                              }}
                            >
                              <option value="" disabled>Selecionar jogador...</option>
                              {members
                                .filter(m => !assignedUserIds.has(m.id))
                                .map(m => (
                                  <option key={m.id} value={m.id}>{m.username}</option>
                                ))}
                              <option value="__skip__">Pular jogador</option>
                            </select>
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ))}

              {/* Match results */}
              <div>
                <p className="text-sm font-semibold mb-2">Resultados</p>
                {parsedMatches.length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {parsedMatches.map((m, i) => (
                      <span key={i} className="text-xs bg-muted rounded-full px-3 py-1 font-medium">
                        {m.team1ColorName} {m.team1Score} × {m.team2Score} {m.team2ColorName}
                      </span>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-muted-foreground flex items-center gap-1.5">
                    <AlertTriangle className="h-3.5 w-3.5 text-orange-500 flex-shrink-0" />
                    Nenhum resultado encontrado na mensagem. Você pode inserir os resultados manualmente depois.
                  </p>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-5 py-4 border-t border-border flex justify-between gap-2">
          <div>
            {step === 2 && (
              <Button variant="outline" onClick={() => setStep(1)}>
                Voltar
              </Button>
            )}
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={onClose}>Cancelar</Button>
            {step === 1 ? (
              <Button variant="gradient" onClick={handleAnalyze} disabled={!text.trim()}>
                Analisar Mensagem
              </Button>
            ) : (
              <Button
                variant="gradient"
                onClick={handleImport}
                disabled={!allResolved || loading}
              >
                {loading ? 'Importando...' : 'Confirmar e Importar'}
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
