import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { financeReasonSchema, type FinanceCommand } from "@/schemas/finance"
import type { FinanceResult } from "@/types/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceFormError } from "@/components/finance/FinanceFields"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { applyServerErrors } from "@/lib/form-errors"
import { toast } from "sonner"

export interface FinanceReasonAction { title: string; description: string; signature: string; run: (command: FinanceCommand) => Promise<FinanceResult> }
export function FinanceReasonForm({ finance, action, onClose }: { finance: FinanceController; action: FinanceReasonAction; onClose: () => void }) {
  const form = useForm({ resolver: zodResolver(financeReasonSchema), defaultValues: { reason: "" } })
  return <Dialog open onOpenChange={open => { if (!open && !finance.pending) onClose() }}><DialogContent>
    <DialogHeader><DialogTitle>{action.title}</DialogTitle><DialogDescription>{action.description}</DialogDescription></DialogHeader>
    <form noValidate onSubmit={form.handleSubmit(async values => {
      try { await finance.mutate(`${action.signature}:${values.reason}`, values.reason, action.run); toast.success("Alteração registrada"); onClose() }
      catch (e) { applyServerErrors(form, e, "Não foi possível registrar a alteração") }
    })} className="flex flex-col gap-5"><FieldGroup><FinanceInput control={form.control} name="reason" label="Motivo" multiline /></FieldGroup>
      <FinanceFormError message={form.formState.errors.root?.message} /><Button variant="brand" type="submit" disabled={finance.pending}>{action.title}</Button>
    </form>
  </DialogContent></Dialog>
}
