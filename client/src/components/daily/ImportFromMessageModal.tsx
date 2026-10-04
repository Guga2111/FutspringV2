import { useState, useMemo } from 'react'
import { CheckCircle2, AlertTriangle, Pencil } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Field, FieldLabel } from '@/components/ui/field'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { getErrorMessage } from '@/lib/errors'
import { parseSessionMessage } from '@/utils/parseSessionMessage'
import { autoMatchPlayers } from '@/utils/matchPlayers'
import { populateFromMessage } from '@/api/dailies'
import type { DailyDetail } from '@/types/daily'
import type { PopulateDailyInput } from '@/api/dailies'

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
          players: (matchedPlayers[i] ?? []).flatMap(p =>
            p.matchedUserId !== null && !p.skipped
              ? [{ userId: p.matchedUserId, totalGoals: p.totalGoals, totalAssists: p.totalAssists }]
              : [],
          ),
        })),
        matches: parsedMatches,
      }
      const updated = await populateFromMessage(daily.id, input)
      toast.success('Sessão importada')
      onSuccess(updated)
    } catch (error) {
      toast.error(getErrorMessage(error, 'Não foi possível importar a sessão'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open onOpenChange={open => !open && !loading && onClose()}>
      <DialogContent className="flex max-h-[90vh] max-w-2xl flex-col">
        <DialogHeader>
          <DialogTitle>Importar da mensagem</DialogTitle>
          <DialogDescription>
            {step === 1 ? 'Passo 1 de 2: cole a mensagem' : 'Passo 2 de 2: confirme os jogadores'}
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto">
          {step === 1 ? (
            <Field data-invalid={parseError !== null}>
              <FieldLabel htmlFor="session-message" className="font-normal text-muted-foreground">
                Cole a mensagem do WhatsApp com os times, jogadores e resultados.
              </FieldLabel>
              <Textarea
                id="session-message"
                className="h-64 resize-none font-mono"
                placeholder={"Azul 🔵\n\nLeal⚽️⚽️⚽️🅰️\nSouto ⚽️🅰️\nFerraz\n\nBranco ⚪️\n\nPedrão ⚽️\n\nAzul 2 x 0 Branco"}
                value={text}
                aria-invalid={parseError !== null}
                onChange={e => setText(e.target.value)}
              />
              {parseError && (
                <p role="alert" className="flex items-center gap-1.5 text-sm text-destructive">
                  <AlertTriangle className="size-4 shrink-0" />
                  {parseError}
                </p>
              )}
            </Field>
          ) : (
            <div className="flex flex-col gap-6">
              {parsedTeams.map((team, teamIdx) => (
                <div key={team.colorName}>
                  <div className="mb-2 flex items-center gap-2">
                    <span
                      className="inline-block size-3 shrink-0 rounded-full border border-border"
                      style={{ backgroundColor: team.colorHex }}
                    />
                    <span className="text-sm font-semibold">{team.colorName}</span>
                  </div>
                  <div className="flex flex-col gap-1.5 pl-5">
                    {(matchedPlayers[teamIdx] ?? []).map((player, playerIdx) => (
                      <div key={`${team.colorName}-${playerIdx}`} className="flex min-h-[28px] flex-wrap items-center gap-2 text-sm">
                        <span className="w-24 shrink-0 truncate text-muted-foreground">{player.rawName}</span>
                        <span className="w-16 shrink-0 text-xs text-muted-foreground">
                          {player.totalGoals > 0 && `⚽${player.totalGoals}`}
                          {player.totalGoals > 0 && player.totalAssists > 0 && ' '}
                          {player.totalAssists > 0 && `🅰️${player.totalAssists}`}
                        </span>
                        {player.matchedUserId !== null ? (
                          <span className="flex items-center gap-1.5 text-xs">
                            <CheckCircle2 className="size-3.5 shrink-0 text-chart-1" />
                            {members.find(m => m.id === player.matchedUserId)?.username}
                            <Button
                              variant="ghost"
                              size="icon"
                              className="size-6"
                              aria-label="Trocar jogador"
                              onClick={() => handlePlayerChange(teamIdx, playerIdx, null, false)}
                            >
                              <Pencil className="size-3" />
                            </Button>
                          </span>
                        ) : player.skipped ? (
                          <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                            Ignorado
                            <Button
                              variant="link"
                              size="sm"
                              className="h-auto p-0 text-xs"
                              onClick={() => handlePlayerChange(teamIdx, playerIdx, null, false)}
                            >
                              Desfazer
                            </Button>
                          </span>
                        ) : (
                          <span className="flex items-center gap-1.5">
                            <AlertTriangle className="size-3.5 shrink-0 text-destructive" />
                            <Select
                              onValueChange={val => {
                                if (val === '__skip__') {
                                  handlePlayerChange(teamIdx, playerIdx, null, true)
                                } else {
                                  handlePlayerChange(teamIdx, playerIdx, Number(val), false)
                                }
                              }}
                            >
                              <SelectTrigger className="h-7 w-44 text-xs" aria-label={`Jogador para ${player.rawName}`}>
                                <SelectValue placeholder="Selecionar jogador..." />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectGroup>
                                  {members
                                    .filter(m => !assignedUserIds.has(m.id))
                                    .map(m => (
                                      <SelectItem key={m.id} value={String(m.id)}>{m.username}</SelectItem>
                                    ))}
                                  <SelectItem value="__skip__">Ignorar jogador</SelectItem>
                                </SelectGroup>
                              </SelectContent>
                            </Select>
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ))}

              <div>
                <p className="mb-2 text-sm font-semibold">Resultados</p>
                {parsedMatches.length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {parsedMatches.map((m, i) => (
                      <Badge key={`${i}-${m.team1ColorName}-${m.team2ColorName}`} variant="secondary">
                        {m.team1ColorName} {m.team1Score} × {m.team2Score} {m.team2ColorName}
                      </Badge>
                    ))}
                  </div>
                ) : (
                  <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    <AlertTriangle className="size-3.5 shrink-0" />
                    Nenhum resultado encontrado na mensagem. Você pode lançar os resultados depois.
                  </p>
                )}
              </div>
            </div>
          )}
        </div>

        <DialogFooter className="gap-2 sm:justify-between">
          <div>
            {step === 2 && (
              <Button variant="outline" onClick={() => setStep(1)}>
                Voltar
              </Button>
            )}
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={onClose} disabled={loading}>Cancelar</Button>
            {step === 1 ? (
              <Button variant="gradient" onClick={handleAnalyze} disabled={!text.trim()}>
                Analisar mensagem
              </Button>
            ) : (
              <Button variant="gradient" onClick={handleImport} disabled={!allResolved || loading}>
                {loading ? 'Importando...' : 'Confirmar e importar'}
              </Button>
            )}
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
