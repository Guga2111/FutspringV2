import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Star } from "lucide-react"
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
import { cn } from "@/lib/utils"
import { playerStarsSchema, type PlayerStarsValues } from "@/schemas/pelada"

// Admin only: stars in this pelada (team sorting weight) for one member or a selection. Saving closes the dialog at
// once; the request runs in usePeladaActions, which reports the result with a toast
export function SetPlayerStarsDialog({
  title = "Definir estrelas",
  description,
  defaultStars,
  requireChange = false,
  onClose,
  onSave,
}: {
  title?: string
  description: string
  defaultStars: number
  // single member: Salvar stays disabled until the value changes
  requireChange?: boolean
  onClose: () => void
  onSave: (stars: number) => void
}) {
  const form = useForm<PlayerStarsValues>({
    resolver: zodResolver(playerStarsSchema),
    defaultValues: { stars: defaultStars },
  })

  function onSubmit(values: PlayerStarsValues) {
    onSave(values.stars)
    onClose()
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
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
          <DialogFooter className="gap-2">
            <Button type="button" variant="ghost" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" variant="brand" disabled={requireChange && !form.formState.isDirty}>
              Salvar
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
