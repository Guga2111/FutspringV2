import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Star } from "lucide-react"
import { toast } from "sonner"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { applyServerErrors } from "@/lib/form-errors"
import { cn } from "@/lib/utils"
import { playerStarsSchema, type PlayerStarsValues } from "@/schemas/pelada"
import type { PeladaMember } from "@/types/pelada"

// Admin only: the member's stars in this pelada (team sorting weight)
export function SetPlayerStarsDialog({
  member,
  onClose,
  onSave,
}: {
  member: PeladaMember
  onClose: () => void
  onSave: (member: PeladaMember, stars: number) => Promise<void>
}) {
  const form = useForm<PlayerStarsValues>({
    resolver: zodResolver(playerStarsSchema),
    defaultValues: { stars: member.stars },
  })

  async function onSubmit(values: PlayerStarsValues) {
    try {
      await onSave(member, values.stars)
      toast.success(`Estrelas de ${member.username} atualizadas`)
      onClose()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível salvar as estrelas")
    }
  }

  const submitting = form.formState.isSubmitting

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Definir estrelas</DialogTitle>
          <DialogDescription>
            Nível de {member.username} nesta pelada, usado para equilibrar os times no sorteio.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex flex-col gap-6">
          <FieldGroup>
            <Controller
              name="stars"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel id="player-stars-label">Estrelas</FieldLabel>
                  <div role="radiogroup" aria-labelledby="player-stars-label" className="flex gap-1">
                    {[1, 2, 3, 4, 5].map((i) => (
                      <Button
                        key={i}
                        type="button"
                        variant="ghost"
                        size="icon"
                        role="radio"
                        aria-checked={field.value === i}
                        aria-label={`${i} estrela${i > 1 ? "s" : ""}`}
                        onClick={() => field.onChange(i)}
                      >
                        <Star className={cn("size-6 text-gold", i <= field.value && "fill-current")} />
                      </Button>
                    ))}
                  </div>
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
          </FieldGroup>
          {form.formState.errors.root && (
            <Alert variant="destructive">
              <AlertDescription>{form.formState.errors.root.message}</AlertDescription>
            </Alert>
          )}
          <DialogFooter className="gap-2">
            <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
              Cancelar
            </Button>
            <Button type="submit" variant="gradient" disabled={submitting || !form.formState.isDirty}>
              {submitting ? "Salvando..." : "Salvar"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
