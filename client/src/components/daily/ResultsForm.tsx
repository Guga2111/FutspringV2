import { ChevronDown, Minus, Plus, Trash2 } from "lucide-react"
import type { DailyDetail } from "@/types/daily"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { cn } from "@/lib/utils"
import { buildPlayerStats } from "@/utils/matchStats"
import type { MatchFormRow } from "@/components/daily/hooks/useMatchResults"

interface ResultsFormProps {
  daily: DailyDetail
  rows: MatchFormRow[]
  loading: boolean
  onClose: () => void
  onUpdateRow: (index: number, patch: Partial<MatchFormRow>) => void
  onUpdateScore: (rowIndex: number, field: "team1Score" | "team2Score", delta: number) => void
  onUpdateStat: (rowIndex: number, userId: number, field: "goals" | "assists", delta: number) => void
  onAddMatch: () => void
  onRemoveMatch: (index: number) => void
  onSubmit: () => void
}

function Stepper({
  value,
  label,
  size = "default",
  onChange,
}: {
  value: number
  label: string
  size?: "default" | "small"
  onChange: (delta: number) => void
}) {
  const buttonClass = size === "small" ? "size-7" : "size-9"
  return (
    <div className="flex items-center gap-2">
      <Button
        type="button"
        variant="outline"
        size="icon"
        className={buttonClass}
        aria-label={`Diminuir ${label}`}
        onClick={() => onChange(-1)}
      >
        <Minus className="size-4" />
      </Button>
      <span className={cn("text-center font-semibold tabular-nums", size === "small" ? "w-6 text-sm" : "w-8 text-xl")}>
        {value}
      </span>
      <Button
        type="button"
        variant="outline"
        size="icon"
        className={buttonClass}
        aria-label={`Aumentar ${label}`}
        onClick={() => onChange(1)}
      >
        <Plus className="size-4" />
      </Button>
    </div>
  )
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
  const teamName = (id: number) => daily.teams.find((t) => t.id === id)?.name ?? `Time ${id}`

  return (
    <Dialog open onOpenChange={(open) => !open && !loading && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Resultados das partidas</DialogTitle>
          <DialogDescription>Lance o placar e, se quiser, os gols e assistências de cada jogador.</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          {rows.length === 0 && (
            <p className="text-sm text-muted-foreground">Sem times disponíveis. Sorteie os times antes dos resultados.</p>
          )}
          {rows.map((row, mi) => {
            const team1PlayerIds = new Set(daily.teams.find((t) => t.id === row.team1Id)?.players.map((p) => p.id) ?? [])
            const groups = [
              { teamId: row.team1Id, stats: row.playerStats.filter((ps) => team1PlayerIds.has(ps.userId)) },
              { teamId: row.team2Id, stats: row.playerStats.filter((ps) => !team1PlayerIds.has(ps.userId)) },
            ]
            return (
              <div key={row.key} className="rounded-lg border p-4">
                <div className="mb-3 flex items-center justify-between">
                  <h3 className="text-sm font-medium">Partida {mi + 1}</h3>
                  {rows.length > 1 && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="text-destructive"
                      onClick={() => onRemoveMatch(mi)}
                    >
                      <Trash2 className="mr-1 size-4" />
                      Remover
                    </Button>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-3">
                  {(["team1Id", "team2Id"] as const).map((side, si) => (
                    <Field key={side} className="gap-1">
                      <FieldLabel htmlFor={`${row.key}-${side}`} className="text-xs text-muted-foreground">
                        Time {si + 1}
                      </FieldLabel>
                      <Select
                        value={String(row[side])}
                        onValueChange={(value) => {
                          const newId = Number(value)
                          const team1Id = side === "team1Id" ? newId : row.team1Id
                          const team2Id = side === "team2Id" ? newId : row.team2Id
                          onUpdateRow(mi, { [side]: newId, playerStats: buildPlayerStats(daily, team1Id, team2Id) })
                        }}
                      >
                        <SelectTrigger id={`${row.key}-${side}`}>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            {daily.teams.map((t) => (
                              <SelectItem key={t.id} value={String(t.id)}>
                                {t.name}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    </Field>
                  ))}
                </div>

                <div className="my-4 flex flex-wrap items-center justify-center gap-4">
                  <Stepper
                    value={row.team1Score}
                    label={`placar do ${teamName(row.team1Id)}`}
                    onChange={(d) => onUpdateScore(mi, "team1Score", d)}
                  />
                  <span className="font-medium text-muted-foreground">x</span>
                  <Stepper
                    value={row.team2Score}
                    label={`placar do ${teamName(row.team2Id)}`}
                    onChange={(d) => onUpdateScore(mi, "team2Score", d)}
                  />
                </div>

                <Collapsible open={row.statsExpanded} onOpenChange={(open) => onUpdateRow(mi, { statsExpanded: open })}>
                  <CollapsibleTrigger asChild>
                    <Button type="button" variant="ghost" size="sm" className="text-muted-foreground">
                      <ChevronDown className={cn("mr-1 size-4 transition-transform", row.statsExpanded && "rotate-180")} />
                      Estatísticas dos jogadores ({row.playerStats.length})
                    </Button>
                  </CollapsibleTrigger>
                  <CollapsibleContent className="flex flex-col gap-4 pt-2">
                    {groups.map(
                      (group) =>
                        group.stats.length > 0 && (
                          <div key={group.teamId} className="flex flex-col gap-2">
                            <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                              {teamName(group.teamId)}
                            </p>
                            {group.stats.map((ps) => (
                              <div key={ps.userId} className="flex flex-col gap-1.5 rounded-md border px-3 py-2">
                                <span className="text-sm font-medium">{ps.username}</span>
                                <div className="flex flex-wrap items-center gap-3">
                                  <div className="flex items-center gap-2">
                                    <span className="w-14 text-xs text-muted-foreground">Gols</span>
                                    <Stepper
                                      size="small"
                                      value={ps.goals}
                                      label={`gols de ${ps.username}`}
                                      onChange={(d) => onUpdateStat(mi, ps.userId, "goals", d)}
                                    />
                                  </div>
                                  <div className="flex items-center gap-2">
                                    <span className="w-14 text-xs text-muted-foreground">Assist.</span>
                                    <Stepper
                                      size="small"
                                      value={ps.assists}
                                      label={`assistências de ${ps.username}`}
                                      onChange={(d) => onUpdateStat(mi, ps.userId, "assists", d)}
                                    />
                                  </div>
                                </div>
                              </div>
                            ))}
                          </div>
                        ),
                    )}
                  </CollapsibleContent>
                </Collapsible>
              </div>
            )
          })}

          {daily.teams.length >= 2 && (
            <Button type="button" variant="outline" className="w-full border-dashed" onClick={onAddMatch}>
              <Plus className="mr-1 size-4" />
              Adicionar partida
            </Button>
          )}
        </div>

        <DialogFooter className="gap-2">
          <Button variant="outline" disabled={loading} onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="gradient" disabled={loading || rows.length === 0} onClick={onSubmit}>
            {loading ? "Salvando..." : "Salvar resultados"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
