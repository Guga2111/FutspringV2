import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import * as api from "@/api/finance"
import { financePaymentFormSchema, type FinancePaymentValues } from "@/schemas/finance"
import type { FinanceCharge, FinanceNotice, FinanceList } from "@/types/finance"
import { financeMethodLabel } from "@/types/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceSelect, FinanceCheck, FinanceFormError } from "@/components/finance/FinanceFields"
import { financeOptions } from "@/utils/finance"
import { Button } from "@/components/ui/button"
import { FieldGroup, Field, FieldLabel, FieldDescription, FieldError } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { applyServerErrors } from "@/lib/form-errors"

const titles: Record<FinancePaymentValues["action"], string> = { receive: "Marcar como pago", partial: "Registrar parcial", confirm: "Confirmar aviso", refund: "Devolver pagamento", report: "Informar pagamento" }
export function FinancePaymentForm({ peladaId, finance, charge, list, action, notice, onClose }: {
  peladaId: number; finance: FinanceController; charge: FinanceCharge; list: FinanceList; action: FinancePaymentValues["action"]; notice?: FinanceNotice; onClose: () => void
}) {
  const form = useForm<FinancePaymentValues>({ resolver: zodResolver(financePaymentFormSchema), defaultValues: {
    action, amount: action === "confirm" ? notice?.amount ?? "0.00" : charge.outstanding, effectiveDate: "", method: "PIX", reason: "", reduceObligation: false, file: null,
  } })
  async function submit(values: FinancePaymentValues) {
    try {
      await finance.mutate(JSON.stringify({ action, charge: charge.id, values: { ...values, file: values.file ? { name: values.file.name, size: values.file.size, modified: values.file.lastModified } : null } }), values.reason, command => {
        if (action === "report") return api.reportFinancePayment(peladaId, list.id, charge.id, command, values.file)
        if (action === "refund") return api.refundFinance(peladaId, list.id, charge.id, command, values)
        if (action === "confirm" && notice) return api.confirmFinanceNotice(peladaId, list.id, charge.id, notice.id, command, values)
        return api.receiveFinance(peladaId, list.id, charge.id, command, values, action === "receive")
      })
      toast.success(action === "report" ? "Pagamento informado para conferência" : "Lançamento registrado")
      onClose()
    } catch (e) { applyServerErrors(form, e, "Não foi possível registrar o pagamento") }
  }
  return <Sheet open onOpenChange={open => { if (!open && !finance.pending) onClose() }}><SheetContent className="flex w-full flex-col gap-5 overflow-y-auto sm:max-w-lg">
    <SheetHeader><SheetTitle>{titles[action]}</SheetTitle><SheetDescription>{charge.name} · {list.name}</SheetDescription></SheetHeader>
    <form onSubmit={form.handleSubmit(submit)} noValidate className="flex flex-col gap-5"><FieldGroup>
      <p className="text-sm">{action === "confirm" ? `Valor informado: R$ ${notice?.amount}` : `Saldo restante: R$ ${charge.outstanding}`}</p>
      {action === "report" ? <>
        <p className="text-sm text-muted-foreground">O gestor vai conferir o saldo restante informado. Este aviso não altera o caixa.</p>
        <Controller control={form.control} name="file" render={({ field, fieldState }) => <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor="finance-receipt">Comprovante (opcional)</FieldLabel><Input id="finance-receipt" type="file" accept="image/jpeg,image/png" onChange={e => field.onChange(e.target.files?.[0] ?? null)} aria-invalid={fieldState.invalid} aria-describedby="receipt-retention" />
          <FieldDescription id="receipt-retention">JPEG ou PNG, até 5 MB. O acesso expira em {list.classification === "DAILY" ? 7 : 30} dias, mesmo sem conferência.</FieldDescription><FieldError errors={[fieldState.error]} />
        </Field>} />
      </> : <>
        {(action === "partial" || action === "refund") && <FinanceInput control={form.control} name="amount" label="Valor (R$)" />}
        <FinanceInput control={form.control} name="effectiveDate" type="date" label="Data efetiva (hoje se não informar)" />
        <FinanceSelect control={form.control} name="method" label="Meio de pagamento" options={financeOptions(financeMethodLabel)} />
        {action === "refund" && <><FinanceCheck control={form.control} name="reduceObligation" label="Também reduzir o valor devido" /><FinanceInput control={form.control} name="reason" label="Motivo da devolução" multiline /></>}
      </>}
    </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button type="submit" disabled={finance.pending}>{finance.pending ? "Registrando…" : titles[action]}</Button></form>
  </SheetContent></Sheet>
}
