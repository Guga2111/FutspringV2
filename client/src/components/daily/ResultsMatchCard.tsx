import { useState } from "react"
import { Controller, useWatch, type Control, type UseFormSetValue } from "react-hook-form"
import { ChevronRight, Trash2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { ScoreStepper } from "@/components/daily/ScoreStepper"
import { TeamColorDot } from "@/components/daily/TeamColorDot"
import { cn } from "@/lib/utils"
import { buildPlayerStats } from "@/utils/matchStats"
import { goalCheck } from "@/utils/liveSession"
import type { ResultsValues } from "@/schemas/daily"
import type { DailyDetail, TeamDTO } from "@/types/daily"

interface ResultsMatchCardProps {
  daily: DailyDetail
  index: number
  number: number
  control: Control<ResultsValues>
  setValue: UseFormSetValue<ResultsValues>
  compact: boolean
  canRemove: boolean
  onRemove: () => void
}

const checkColor = { ok: "text-success", empty: "text-faint-foreground", mismatch: "text-warning" } as const

export function ResultsMatchCard({ daily, index, number, control, setValue, compact, canRemove, onRemove }: ResultsMatchCardProps) {
  const [statsOpen, setStatsOpen] = useState(false)
  const match = useWatch({ control, name: `matches.${index}` })
  const team = (id: number): TeamDTO | undefined => daily.teams.find((t) => t.id === id)
  const team1 = team(match.team1Id)
  const team2 = team(match.team2Id)

  const team1Ids = new Set(team1?.players.map((p) => p.id) ?? [])
  const goalsOf = (ids: Set<number>, inside: boolean) =>
    match.playerStats.filter((s) => ids.has(s.userId) === inside).reduce((sum, s) => sum + s.goals, 0)
  const check = goalCheck(match.team1Score, match.team2Score, goalsOf(team1Ids, true), goalsOf(team1Ids, false))

  function pickTeam(side: "team1Id" | "team2Id", teamId: number) {
    const team1Id = side === "team1Id" ? teamId : match.team1Id
    const team2Id = side === "team2Id" ? teamId : match.team2Id
    setValue(`matches.${index}.${side}`, teamId, { shouldValidate: true })
    // the stats rows follow the two teams on the field
    setValue(`matches.${index}.playerStats`, buildPlayerStats(daily, team1Id, team2Id))
  }

  const scoreStepper = (side: "team1Score" | "team2Score", name: string) => (
    <Controller
      name={`matches.${index}.${side}`}
      control={control}
      render={({ field }) => (
        <ScoreStepper value={field.value} onChange={field.onChange} label={`placar do ${name}`} size={compact ? "lg" : "md"} />
      )}
    />
  )

  return (
    <article className={cn("flex flex-col gap-3.5 rounded-xl border bg-card-elevated", compact ? "p-3" : "p-4")}>
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold">Partida {number}</h3>
        {canRemove && (
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={`Remover partida ${number}`}
            onClick={onRemove}
            className="size-[30px] text-subtle-foreground hover:bg-destructive-muted hover:text-destructive-soft [&_svg]:size-[15px]"
          >
            <Trash2 />
          </Button>
        )}
      </div>

      <div className="grid grid-cols-1 gap-3.5 md:grid-cols-2">
        {(["team1Id", "team2Id"] as const).map((side, si) => {
          const otherId = side === "team1Id" ? match.team2Id : match.team1Id
          return (
            <Controller
              key={side}
              name={`matches.${index}.${side}`}
              control={control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid} className="gap-2">
                  <FieldLabel className="text-xs font-normal text-subtle-foreground">Time {si + 1}</FieldLabel>
                  <ToggleGroup
                    type="single"
                    variant="team"
                    size="chip"
                    value={String(field.value)}
                    onValueChange={(value) => value && pickTeam(side, Number(value))}
                    aria-label={`Time ${si + 1} da partida ${number}`}
                    className={cn("justify-start gap-1.5", compact ? "scrollbar-none flex-nowrap overflow-x-auto" : "flex-wrap")}
                  >
                    {daily.teams.map((t) => (
                      <ToggleGroupItem key={t.id} value={String(t.id)} disabled={t.id === otherId}>
                        <TeamColorDot color={t.color} className="size-[9px]" />
                        {t.name}
                      </ToggleGroupItem>
                    ))}
                  </ToggleGroup>
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
          )
        })}
      </div>

      {compact ? (
        <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-1.5 rounded-xl bg-background px-1.5 py-3">
          {[
            { t: team1, side: "team1Score" as const },
            { t: team2, side: "team2Score" as const },
          ].map(({ t, side }, i) => (
            <div key={side} className={cn("flex min-w-0 flex-col items-center gap-2", i === 1 && "col-start-3")}>
              <span className="flex max-w-full items-center gap-1.5 truncate text-sm font-semibold">
                <TeamColorDot color={t?.color ?? null} className="size-[9px]" />
                <span className="truncate">{t?.name}</span>
              </span>
              {scoreStepper(side, t?.name ?? "time")}
            </div>
          ))}
          <span className="col-start-2 row-start-1 pt-[26px] text-[13px] text-faint-foreground">x</span>
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-center gap-4 py-1.5">
          <span className="flex min-w-[90px] items-center justify-end gap-2 text-[15px] font-semibold">
            <TeamColorDot color={team1?.color ?? null} />
            {team1?.name}
          </span>
          {scoreStepper("team1Score", team1?.name ?? "time 1")}
          <span className="text-sm text-faint-foreground">x</span>
          {scoreStepper("team2Score", team2?.name ?? "time 2")}
          <span className="flex min-w-[90px] items-center gap-2 text-[15px] font-semibold">
            {team2?.name}
            <TeamColorDot color={team2?.color ?? null} />
          </span>
        </div>
      )}

      <Collapsible open={statsOpen} onOpenChange={setStatsOpen} className="flex flex-col gap-3 border-t pt-3">
        <div className="flex flex-wrap items-center gap-2.5">
          <CollapsibleTrigger className="flex items-center gap-2 py-1 text-sm font-medium text-secondary-foreground">
            <ChevronRight className={cn("size-[15px] transition-transform duration-150", statsOpen && "rotate-90")} />
            Gols e assistências
          </CollapsibleTrigger>
          <span className={cn("ml-auto text-xs font-medium", checkColor[check.state])}>{check.label}</span>
        </div>
        <CollapsibleContent className="grid grid-cols-1 gap-3.5 md:grid-cols-2">
          {[team1, team2].map((t, side) =>
            t ? (
              <div key={`${side}-${t.id}`} className="flex flex-col gap-0.5">
                <div className="flex items-center gap-2 pb-1.5">
                  <TeamColorDot color={t.color} className="size-[9px]" />
                  <span className="text-xs font-semibold uppercase tracking-[0.05em] text-muted-foreground">{t.name}</span>
                  <span className="ml-auto text-[11px] text-faint-foreground">Gols · Assist.</span>
                </div>
                {match.playerStats.map((stat, si) =>
                  team1Ids.has(stat.userId) === (side === 0) ? (
                    <div key={stat.userId} className={cn("flex items-center border-t", compact ? "gap-1.5 py-2" : "gap-2 py-1.5")}>
                      <span className="line-clamp-2 min-w-0 flex-1 break-words text-[13px] font-medium leading-tight">{stat.username}</span>
                      <Controller
                        name={`matches.${index}.playerStats.${si}.goals`}
                        control={control}
                        render={({ field }) => (
                          <ScoreStepper value={field.value} onChange={field.onChange} label={`gols de ${stat.username}`} size="sm" />
                        )}
                      />
                      <span aria-hidden className="h-4 w-px bg-input" />
                      <Controller
                        name={`matches.${index}.playerStats.${si}.assists`}
                        control={control}
                        render={({ field }) => (
                          <ScoreStepper value={field.value} onChange={field.onChange} label={`assistências de ${stat.username}`} size="sm" />
                        )}
                      />
                    </div>
                  ) : null,
                )}
              </div>
            ) : null,
          )}
        </CollapsibleContent>
      </Collapsible>
    </article>
  )
}
