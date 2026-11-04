import { useState } from "react"
import { useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import * as api from "@/api/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import type { FinanceCharge, FinanceList } from "@/types/finance"
import { financeMethodLabel } from "@/types/finance"
import { financeReceiveAllSchema, type FinanceReceiveAllValues } from "@/schemas/finance"
import { FinanceInput, FinanceSelect, FinanceFormError } from "@/components/finance/FinanceFields"
import { financeOptions, formatFinanceMoney } from "@/utils/finance"
import { applyServerErrors } from "@/lib/form-errors"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog"
import { formatShortDate } from "@/utils/dates"

const SHOWN_NAMES = 5

// Confirms "Receber selecionados": who, how much, when and how; the server receives everything or nothing.
// onReceived runs only on success (the list leaves selection mode); on an error the selection stays
export function FinanceReceiveAllDialog({ peladaId, finance, list, charges, total, review, onReceived, onClose }: {
  peladaId: number; finance: FinanceController; list: FinanceList; charges: FinanceCharge[]; total: string; review: number; onReceived: () => void; onClose: () => void
}) {
  const [options, setOptions] = useState(false)
  const form = useForm<FinanceReceiveAllValues>({ resolver: zodResolver(financeReceiveAllSchema), defaultValues: { effectiveDate: "", method: "PIX" } })
  const date = useWatch({ control: form.control, name: "effectiveDate" })
  const method = useWatch({ control: form.control, name: "method" })
  const busy = finance.pending || form.formState.isSubmitting
  const ids = charges.map(c => c.id)
  const names = charges.slice(0, SHOWN_NAMES).map(c => c.name)
  const more = charges.length - names.length
  async function submit(values: FinanceReceiveAllValues) {
    try {
      await finance.mutate(JSON.stringify({ receiveAll: list.id, ids, values }), "", command => api.receiveAllFinance(peladaId, list.id, command, ids, values))
      toast.success(charges.length === 1 ? "1 pagamento registrado" : `${charges.length} pagamentos registrados`)
      onReceived()
      onClose()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível registrar os pagamentos")
      if (form.formState.errors.effectiveDate || form.formState.errors.method) setOptions(true)
    }
  }
  return <AlertDialog open onOpenChange={open => { if (!open && !busy) onClose() }}>
    <AlertDialogContent>
      <form onSubmit={form.handleSubmit(submit)} noValidate className="flex flex-col gap-4">
        <AlertDialogHeader>
          <AlertDialogTitle>Receber {charges.length === 1 ? "1 pagamento" : `${charges.length} pagamentos`} · {formatFinanceMoney(total)}</AlertDialogTitle>
          <AlertDialogDescription>
            Cada pessoa fica quitada com o que ainda devia: {names.join(", ")}{more > 0 ? ` e mais ${more}` : ""}.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {review > 0 && <p className="text-body-sm text-muted-foreground">{review === 1 ? "1 pessoa com pagamento para conferir não entra." : `${review} pessoas com pagamento para conferir não entram.`} Confira cada uma antes.</p>}
        <Collapsible open={options} onOpenChange={setOptions}>
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm">{date ? formatShortDate(date) : "Hoje"} · {financeMethodLabel[method]}</p>
            <CollapsibleTrigger asChild><Button type="button" variant="ghost" size="sm">{options ? "Recolher" : "Alterar"}</Button></CollapsibleTrigger>
          </div>
          <CollapsibleContent className="pt-4">
            <FieldGroup>
              <FinanceInput control={form.control} name="effectiveDate" type="date" label="Data do recebimento" />
              <FinanceSelect control={form.control} name="method" label="Meio de pagamento" options={financeOptions(financeMethodLabel)} />
            </FieldGroup>
          </CollapsibleContent>
        </Collapsible>
        <FinanceFormError message={form.formState.errors.root?.message} />
        <AlertDialogFooter>
          <AlertDialogCancel type="button" disabled={busy}>Cancelar</AlertDialogCancel>
          <Button variant="brand" type="submit" disabled={busy}>{busy ? "Registrando…" : `Receber ${formatFinanceMoney(total)}`}</Button>
        </AlertDialogFooter>
      </form>
    </AlertDialogContent>
  </AlertDialog>
}
