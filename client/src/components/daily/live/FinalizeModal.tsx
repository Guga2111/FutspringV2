import { Controller } from "react-hook-form"
import type { DailyDetail, PlayerDTO } from "@/types/daily"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldError, FieldLabel, FieldLegend, FieldSet } from "@/components/ui/field"
import { useFinalizeForm } from "@/components/daily/hooks/useFinalizeForm"

interface FinalizeModalProps {
  daily: DailyDetail
  onClose: () => void
  onSuccess: (updated: DailyDetail) => void
}

interface PlayerCheckboxListProps {
  name: string
  legend: string
  players: PlayerDTO[]
  value: number[]
  onChange: (ids: number[]) => void
  invalid: boolean
  error?: { message?: string }
}

function PlayerCheckboxList({ name, legend, players, value, onChange, invalid, error }: PlayerCheckboxListProps) {
  function toggle(playerId: number) {
    onChange(value.includes(playerId) ? value.filter((id) => id !== playerId) : [...value, playerId])
  }

  return (
    <FieldSet className="gap-1.5" data-invalid={invalid}>
      <FieldLegend variant="label" className="mb-1.5">
        {legend}
        {value.length > 0 && (
          <span className="ml-2 text-xs font-normal text-muted-foreground">
            ({value.length} selecionado{value.length > 1 ? "s" : ""})
          </span>
        )}
      </FieldLegend>
      <div className="max-h-48 divide-y divide-border overflow-y-auto rounded-md border border-border">
        {players.map((p) => {
          const id = `${name}-${p.id}`
          return (
            <Field key={p.id} orientation="horizontal" className="px-3 py-2 hover:bg-muted/50">
              <Checkbox
                id={id}
                checked={value.includes(p.id)}
                onCheckedChange={() => toggle(p.id)}
                aria-invalid={invalid}
              />
              <FieldLabel htmlFor={id} className="cursor-pointer font-normal">
                {p.username}
              </FieldLabel>
            </Field>
          )
        })}
      </div>
      <FieldError errors={[error]} />
    </FieldSet>
  )
}

export default function FinalizeModal({ daily, onClose, onSuccess }: FinalizeModalProps) {
  const { form, sessionPlayers, submit } = useFinalizeForm(daily, onSuccess)
  const submitting = form.formState.isSubmitting
  const rootError = form.formState.errors.root

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Finalizar sessão</DialogTitle>
          <DialogDescription>Escolha os premiados; as estatísticas e o ranking serão calculados.</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} noValidate className="flex flex-col gap-4">
          <Controller
            name="puskasWinnerIds"
            control={form.control}
            render={({ field, fieldState }) => (
              <PlayerCheckboxList
                name="puskas"
                legend="Puskás"
                players={sessionPlayers}
                value={field.value}
                onChange={field.onChange}
                invalid={fieldState.invalid}
                error={fieldState.error}
              />
            )}
          />
          <Controller
            name="wiltballWinnerIds"
            control={form.control}
            render={({ field, fieldState }) => (
              <PlayerCheckboxList
                name="bola-murcha"
                legend="Bola Murcha"
                players={sessionPlayers}
                value={field.value}
                onChange={field.onChange}
                invalid={fieldState.invalid}
                error={fieldState.error}
              />
            )}
          />
          {rootError && (
            <Alert variant="destructive">
              <AlertDescription>{rootError.message}</AlertDescription>
            </Alert>
          )}
          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" disabled={submitting} onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" variant="brand" disabled={submitting}>
              {submitting ? "Finalizando..." : "Finalizar"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
