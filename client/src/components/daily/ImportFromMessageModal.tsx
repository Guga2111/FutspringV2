import { Controller } from "react-hook-form"
import { HugeiconsIcon } from "@hugeicons/react"
import { Alert02Icon, CheckmarkCircle02Icon, PencilEdit02Icon } from "@hugeicons/core-free-icons"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { useImportFromMessage } from "@/components/daily/hooks/useImportFromMessage"
import type { ImportResolutionValues } from "@/schemas/daily"
import type { DailyDetail, PlayerDTO } from "@/types/daily"

type ImportPlayer = ImportResolutionValues["teams"][number]["players"][number]

const SKIP = "__skip__"

interface Props {
  daily: DailyDetail
  onClose: () => void
  onSuccess: (updated: DailyDetail) => void
}

interface ImportPlayerRowProps {
  player: ImportPlayer
  onChange: (player: ImportPlayer) => void
  error?: string
  members: PlayerDTO[]
  linkedIds: Set<number>
}

// One name of the message: linked (✓ member, change), skipped (Desfazer) or waiting for a member or "Ignorar"
function ImportPlayerRow({ player, onChange, error, members, linkedIds }: ImportPlayerRowProps) {
  const set = (matchedUserId: number | null, skipped: boolean) => onChange({ ...player, matchedUserId, skipped })

  return (
    <div className="flex min-h-[28px] flex-wrap items-center gap-2 text-sm">
      <span className="w-24 shrink-0 truncate text-muted-foreground">{player.rawName}</span>
      <span className="w-16 shrink-0 text-xs text-muted-foreground">
        {player.totalGoals > 0 && `⚽${player.totalGoals}`}
        {player.totalGoals > 0 && player.totalAssists > 0 && " "}
        {player.totalAssists > 0 && `🅰️${player.totalAssists}`}
      </span>
      {player.matchedUserId !== null ? (
        <span className="flex items-center gap-1.5 text-xs">
          <HugeiconsIcon icon={CheckmarkCircle02Icon} className="size-3.5 shrink-0 text-chart-1" />
          {members.find((m) => m.id === player.matchedUserId)?.username}
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="size-6"
            aria-label="Trocar jogador"
            onClick={() => set(null, false)}
          >
            <HugeiconsIcon icon={PencilEdit02Icon} className="size-3" />
          </Button>
        </span>
      ) : player.skipped ? (
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
          Ignorado
          <Button type="button" variant="link" size="sm" className="h-auto p-0 text-xs" onClick={() => set(null, false)}>
            Desfazer
          </Button>
        </span>
      ) : (
        <span className="flex items-center gap-1.5">
          <HugeiconsIcon icon={Alert02Icon} className="size-3.5 shrink-0 text-destructive" />
          <Select onValueChange={(value) => (value === SKIP ? set(null, true) : set(Number(value), false))}>
            <SelectTrigger
              className="h-7 w-44 text-xs"
              aria-label={`Jogador para ${player.rawName}`}
              aria-invalid={error !== undefined}
            >
              <SelectValue placeholder="Selecionar jogador..." />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {members
                  .filter((m) => !linkedIds.has(m.id))
                  .map((m) => (
                    <SelectItem key={m.id} value={String(m.id)}>
                      {m.username}
                    </SelectItem>
                  ))}
                <SelectItem value={SKIP}>Ignorar jogador</SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
        </span>
      )}
      {error && <FieldError className="w-full text-xs">{error}</FieldError>}
    </div>
  )
}

export default function ImportFromMessageModal({ daily, onClose, onSuccess }: Props) {
  const { step, back, parsed, members, linkedIds, messageForm, resolutionForm, analyze, submit } = useImportFromMessage(
    daily,
    onSuccess,
  )
  const submitting = resolutionForm.formState.isSubmitting
  const rootError = resolutionForm.formState.errors.root

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="flex max-h-[90vh] max-w-2xl flex-col">
        <DialogHeader>
          <DialogTitle>Importar da mensagem</DialogTitle>
          <DialogDescription>
            {step === 1 ? "Passo 1 de 2: cole a mensagem" : "Passo 2 de 2: confirme os jogadores"}
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto">
          {step === 1 || !parsed ? (
            <form id="import-message" onSubmit={analyze} noValidate>
              <Controller
                name="message"
                control={messageForm.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="session-message" className="font-normal text-muted-foreground">
                      Cole a mensagem do WhatsApp com os times, jogadores e resultados.
                    </FieldLabel>
                    <Textarea
                      {...field}
                      id="session-message"
                      className="h-64 resize-none font-mono"
                      placeholder={"Azul 🔵\n\nLeal⚽️⚽️⚽️🅰️\nSouto ⚽️🅰️\nFerraz\n\nBranco ⚪️\n\nPedrão ⚽️\n\nAzul 2 x 0 Branco"}
                      aria-invalid={fieldState.invalid}
                    />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
            </form>
          ) : (
            <form id="import-resolution" onSubmit={submit} noValidate className="flex flex-col gap-6">
              {parsed.teams.map((team, teamIdx) => (
                <div key={team.colorName}>
                  <div className="mb-2 flex items-center gap-2">
                    <span
                      className="inline-block size-3 shrink-0 rounded-full border border-border"
                      style={{ backgroundColor: team.colorHex }}
                    />
                    <span className="text-sm font-semibold">{team.colorName}</span>
                  </div>
                  <div className="flex flex-col gap-1.5 pl-5">
                    {team.players.map((_, playerIdx) => (
                      <Controller
                        // the parsed names have no id; the list is fixed until the message is parsed again
                        key={`${team.colorName}-${playerIdx}`}
                        name={`teams.${teamIdx}.players.${playerIdx}`}
                        control={resolutionForm.control}
                        render={({ field, fieldState }) => (
                          <ImportPlayerRow
                            player={field.value}
                            onChange={field.onChange}
                            error={fieldState.error?.message}
                            members={members}
                            linkedIds={linkedIds}
                          />
                        )}
                      />
                    ))}
                  </div>
                </div>
              ))}

              <div>
                <p className="mb-2 text-sm font-semibold">Resultados</p>
                {parsed.matches.length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {parsed.matches.map((m, i) => (
                      <Badge key={`${i}-${m.team1ColorName}-${m.team2ColorName}`} variant="secondary">
                        {m.team1ColorName} {m.team1Score} × {m.team2Score} {m.team2ColorName}
                      </Badge>
                    ))}
                  </div>
                ) : (
                  <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    <HugeiconsIcon icon={Alert02Icon} className="size-3.5 shrink-0" />
                    Nenhum resultado encontrado na mensagem. Você pode lançar os resultados depois.
                  </p>
                )}
              </div>

              {rootError && (
                <Alert variant="destructive">
                  <AlertDescription>{rootError.message}</AlertDescription>
                </Alert>
              )}
            </form>
          )}
        </div>

        <DialogFooter className="gap-2 sm:justify-between">
          <div>
            {step === 2 && (
              <Button type="button" variant="outline" onClick={back} disabled={submitting}>
                Voltar
              </Button>
            )}
          </div>
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancelar
            </Button>
            {step === 1 ? (
              <Button type="submit" form="import-message" variant="brand">
                Analisar mensagem
              </Button>
            ) : (
              <Button type="submit" form="import-resolution" variant="brand" disabled={submitting}>
                {submitting ? "Importando..." : "Confirmar e importar"}
              </Button>
            )}
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
