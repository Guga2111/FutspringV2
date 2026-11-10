import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { FinanceFormError, FinanceInput } from "@/components/finance/FinanceFields"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"
import { applyServerErrors } from "@/lib/form-errors"
import { voidPaymentSchema, type VoidPaymentValues } from "@/schemas/platform"
import type { PlatformPayment } from "@/types/platform"
import { formatFinanceMoney } from "@/utils/finance"
import { formatIsoDate } from "@/utils/platform"

interface VoidPaymentDialogProps {
  payment: PlatformPayment
  onSubmit: (reason: string) => Promise<void>
  onClose: () => void
}

// "Estornar": the payment leaves every total; the validity stays as it is (adjusted apart)
export function VoidPaymentDialog({ payment, onSubmit, onClose }: VoidPaymentDialogProps) {
  const form = useForm<VoidPaymentValues>({ resolver: zodResolver(voidPaymentSchema), defaultValues: { reason: "" } })

  async function submit({ reason }: VoidPaymentValues) {
    try {
      await onSubmit(reason)
      onClose()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível estornar o pagamento")
    }
  }

  const busy = form.formState.isSubmitting
  return (
    <AlertDialog open onOpenChange={(open) => !open && !busy && onClose()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            Estornar {formatFinanceMoney(payment.amount)} de {formatIsoDate(payment.paidOn)}?
          </AlertDialogTitle>
          <AlertDialogDescription>
            O pagamento sai dos totais e fica no histórico. A validade não muda: ajuste-a à parte se precisar.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(submit)}>
          <FieldGroup>
            <FinanceInput control={form.control} name="reason" label="Motivo" multiline />
          </FieldGroup>
          <FinanceFormError message={form.formState.errors.root?.message} />
          <AlertDialogFooter>
            <AlertDialogCancel disabled={busy}>Voltar</AlertDialogCancel>
            <Button type="submit" variant="destructive" disabled={busy}>
              Estornar
            </Button>
          </AlertDialogFooter>
        </form>
      </AlertDialogContent>
    </AlertDialog>
  )
}
