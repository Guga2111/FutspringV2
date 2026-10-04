import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { format } from "date-fns"
import { ptBR } from "date-fns/locale"
import { CalendarIcon } from "lucide-react"
import { toast } from "sonner"
import { createDaily } from "@/api/dailies"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { applyServerErrors } from "@/lib/form-errors"
import { cn } from "@/lib/utils"
import { createDailySchema, type CreateDailyInput, type CreateDailyValues } from "@/schemas/daily"

export function CreateSessionDialog({
  peladaId,
  defaultTime = "08:00",
  onClose,
  onCreated,
}: {
  peladaId: number
  defaultTime?: string
  onClose: () => void
  onCreated: () => void
}) {
  const [calendarOpen, setCalendarOpen] = useState(false)
  const form = useForm<CreateDailyInput, unknown, CreateDailyValues>({
    resolver: zodResolver(createDailySchema),
    defaultValues: { dailyDate: null, dailyTime: defaultTime.slice(0, 5) },
  })

  async function onSubmit(values: CreateDailyValues) {
    try {
      await createDaily(peladaId, { dailyDate: format(values.dailyDate, "yyyy-MM-dd"), dailyTime: values.dailyTime })
      toast.success("Sessão criada")
      onCreated()
      onClose()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível criar a sessão")
    }
  }

  const submitting = form.formState.isSubmitting

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Criar sessão</DialogTitle>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex flex-col gap-6">
          <FieldGroup className="gap-5">
            <Controller
              name="dailyDate"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="dailyDate">Data</FieldLabel>
                  <Popover open={calendarOpen} onOpenChange={setCalendarOpen}>
                    <PopoverTrigger asChild>
                      <Button
                        id="dailyDate"
                        type="button"
                        variant="outline"
                        aria-invalid={fieldState.invalid}
                        className={cn("w-full justify-start rounded-md font-normal", !field.value && "text-muted-foreground")}
                      >
                        <CalendarIcon className="mr-2 size-4" />
                        {field.value ? format(field.value, "PPP", { locale: ptBR }) : "Escolha uma data"}
                      </Button>
                    </PopoverTrigger>
                    <PopoverContent className="w-auto p-0" align="start">
                      <Calendar
                        mode="single"
                        selected={field.value ?? undefined}
                        onSelect={(date) => {
                          field.onChange(date ?? null)
                          setCalendarOpen(false)
                        }}
                        initialFocus
                      />
                    </PopoverContent>
                  </Popover>
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <Controller
              name="dailyTime"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="dailyTime">Horário</FieldLabel>
                  <Input {...field} id="dailyTime" type="time" aria-invalid={fieldState.invalid} />
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
            <Button type="submit" variant="gradient" disabled={submitting}>
              {submitting ? "Criando..." : "Criar"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
