import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { FinanceFormError, FinanceInput } from "@/components/finance/FinanceFields"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { applyServerErrors } from "@/lib/form-errors"
import { subscriptionActionSchema, type SubscriptionActionValues, type SubscriptionFormAction } from "@/schemas/platform"

const COPY: Record<SubscriptionFormAction, { title: string; description: string; submit: string; destructive?: boolean }> = {
  SET_VALID_UNTIL: {
    title: "Definir validade",
    description: "A assinatura passa a ser paga até o fim desse dia (horário de São Paulo). Uma data no passado trava a pelada.",
    submit: "Definir validade",
  },
  EXTEND_DAYS: {
    title: "Estender dias",
    description: "Soma dias à validade atual, ou a hoje se ela já passou. Mantém o tipo (teste grátis ou paga).",
    submit: "Estender",
  },
  EXEMPT: {
    title: "Isentar pelada",
    description: "A pelada fica isenta, sem validade, e continua escrevendo sempre.",
    submit: "Isentar",
  },
  CANCEL: {
    title: "Cancelar assinatura",
    description: "A pelada fica em somente leitura até um novo pagamento ser registrado.",
    submit: "Cancelar assinatura",
    destructive: true,
  },
}

const DEFAULTS: Record<SubscriptionFormAction, SubscriptionActionValues> = {
  SET_VALID_UNTIL: { action: "SET_VALID_UNTIL", validUntil: "", note: "" },
  EXTEND_DAYS: { action: "EXTEND_DAYS", days: 30, note: "" },
  EXEMPT: { action: "EXEMPT", note: "" },
  CANCEL: { action: "CANCEL", note: "" },
}

interface SubscriptionActionDialogProps {
  action: SubscriptionFormAction
  onSubmit: (values: SubscriptionActionValues) => Promise<void>
  onClose: () => void
}

// One dialog for the subscription's actions: the action's own field (a date, days or none) and a required note
export function SubscriptionActionDialog({ action, onSubmit, onClose }: SubscriptionActionDialogProps) {
  const copy = COPY[action]
  const form = useForm<SubscriptionActionValues>({ resolver: zodResolver(subscriptionActionSchema), defaultValues: DEFAULTS[action] })

  async function submit(values: SubscriptionActionValues) {
    try {
      await onSubmit(values)
      onClose()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível alterar a assinatura")
    }
  }

  const busy = form.formState.isSubmitting
  return (
    <Dialog open onOpenChange={(open) => !open && !busy && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{copy.title}</DialogTitle>
          <DialogDescription>{copy.description}</DialogDescription>
        </DialogHeader>
        <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(submit)}>
          <FieldGroup>
            {action === "SET_VALID_UNTIL" && <FinanceInput control={form.control} name="validUntil" label="Válida até" type="date" />}
            {action === "EXTEND_DAYS" && (
              <Controller
                control={form.control}
                name="days"
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="platform-days">Dias</FieldLabel>
                    <Input
                      id="platform-days"
                      type="number"
                      inputMode="numeric"
                      min={1}
                      max={366}
                      value={Number.isNaN(field.value) ? "" : field.value}
                      onChange={(e) => field.onChange(e.target.valueAsNumber)}
                      onBlur={field.onBlur}
                      aria-invalid={fieldState.invalid}
                    />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
            )}
            <FinanceInput control={form.control} name="note" label="Nota" multiline placeholder="Por que a mudança" />
          </FieldGroup>
          <FinanceFormError message={form.formState.errors.root?.message} />
          <DialogFooter>
            <Button type="button" variant="outline" disabled={busy} onClick={onClose}>
              Voltar
            </Button>
            <Button type="submit" variant={copy.destructive ? "destructive" : "brand"} disabled={busy}>
              {copy.submit}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
