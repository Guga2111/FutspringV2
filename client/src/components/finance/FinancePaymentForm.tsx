import { useState } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import * as api from "@/api/finance"
import { financeReceiveFormSchema, type FinancePaymentValues } from "@/schemas/finance"
import type { FinanceCharge, FinanceNotice, FinanceList } from "@/types/finance"
import { financeMethodLabel } from "@/types/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceSelect, FinanceCheck, FinanceFormError } from "@/components/finance/FinanceFields"
import { financeOptions, formatFinanceMoney } from "@/utils/finance"
import { Button } from "@/components/ui/button"
import { FieldGroup, Field, FieldLabel, FieldDescription, FieldError } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { applyServerErrors } from "@/lib/form-errors"

const titles: Record<FinancePaymentValues["action"], string> = { receive: "Receber pagamento", partial: "Receber pagamento", confirm: "Conferir pagamento", refund: "Devolver pagamento", report: "Informar pagamento" }
export function FinancePaymentForm({ peladaId, finance, charge, list, action, notice, onClose }: {
  peladaId: number; finance: FinanceController; charge: FinanceCharge; list: FinanceList; action: FinancePaymentValues["action"]; notice?: FinanceNotice; onClose: () => void
}) {
  const [options, setOptions] = useState(false)
  const form = useForm<FinancePaymentValues>({ resolver: zodResolver(financeReceiveFormSchema), defaultValues: {
    action, amount: action === "confirm" ? notice?.amount ?? "0.00" : action === "refund" ? charge.received : charge.outstanding,
    effectiveDate: "", method: "PIX", reason: "", reduceObligation: false, file: null,
  } })
  const date = useWatch({ control: form.control, name: "effectiveDate" })
  const method = useWatch({ control: form.control, name: "method" })
  async function submit(values: FinancePaymentValues) {
    try {
      await finance.mutate(JSON.stringify({ action, charge: charge.id, values: { ...values, file: values.file ? { name: values.file.name, size: values.file.size, modified: values.file.lastModified } : null } }), values.reason, command => {
        if (action === "report") return api.reportFinancePayment(peladaId, list.id, charge.id, command, values.file)
        if (action === "refund") return api.refundFinance(peladaId, list.id, charge.id, command, values)
        if (action === "confirm" && notice) return api.confirmFinanceNotice(peladaId, list.id, charge.id, notice.id, command, values)
        return api.receiveFinance(peladaId, list.id, charge.id, command, values)
      })
      toast.success(action === "report" ? "Pagamento enviado para conferência" : "Pagamento registrado")
      onClose()
    } catch (error) { applyServerErrors(form, error, "Não foi possível registrar o pagamento") }
  }
  const busy = finance.pending || form.formState.isSubmitting
  return <Sheet open onOpenChange={open => { if (!open && !busy) onClose() }}><SheetContent className="flex w-full flex-col gap-6 overflow-y-auto sm:max-w-lg">
    <SheetHeader><SheetTitle>{titles[action]}</SheetTitle><SheetDescription>{charge.name} · {list.name}</SheetDescription></SheetHeader>
    <form onSubmit={form.handleSubmit(submit)} noValidate className="flex flex-1 flex-col gap-6"><FieldGroup>
      {action === "report" || action === "confirm" ? <div><p className="text-sm text-muted-foreground">{action === "confirm" ? "Valor informado pelo jogador" : "Valor que você está informando"}</p><p className="mt-1 text-2xl font-semibold tabular-nums">{formatFinanceMoney(action === "confirm" ? notice?.amount ?? "0.00" : charge.outstanding)}</p></div> : <><FinanceInput control={form.control} name="amount" label={action === "refund" ? "Quanto será devolvido? (R$)" : "Quanto você recebeu? (R$)"} /><p className="text-sm text-muted-foreground">{action === "refund" ? `Recebido até agora: ${formatFinanceMoney(charge.received)}` : `Falta receber ${formatFinanceMoney(charge.outstanding)}. Para um parcial, informe o valor recebido.`}</p></>}
      {action === "report" ? <Controller control={form.control} name="file" render={({ field, fieldState }) => <Field data-invalid={fieldState.invalid}>
        <FieldLabel htmlFor="finance-receipt">Comprovante (opcional)</FieldLabel><Input id="finance-receipt" type="file" accept="image/jpeg,image/png" onChange={event => field.onChange(event.target.files?.[0] ?? null)} aria-invalid={fieldState.invalid} aria-describedby="receipt-retention" />
        <FieldDescription id="receipt-retention">JPEG ou PNG, até 5 MB. Disponível por {list.classification === "DAILY" ? 7 : 30} dias. O gestor ainda precisa conferir.</FieldDescription><FieldError errors={[fieldState.error]} />
      </Field>} /> : <Collapsible open={options} onOpenChange={setOptions}><div className="flex items-center justify-between gap-3"><p className="text-sm">{date ? date.split("-").reverse().join("/") : "Hoje"} · {financeMethodLabel[method]}</p><CollapsibleTrigger asChild><Button type="button" variant="ghost" size="sm">{options ? "Recolher" : "Alterar"}</Button></CollapsibleTrigger></div><CollapsibleContent className="pt-4"><FieldGroup><FinanceInput control={form.control} name="effectiveDate" type="date" label={action === "refund" ? "Data da devolução" : "Data do recebimento"} /><FinanceSelect control={form.control} name="method" label="Meio de pagamento" options={financeOptions(financeMethodLabel)} /></FieldGroup></CollapsibleContent></Collapsible>}
      {action === "refund" && <><FinanceCheck control={form.control} name="reduceObligation" label="Também diminuir o valor da cobrança" /><FinanceInput control={form.control} name="reason" label="Motivo da devolução" multiline /></>}
      {action === "confirm" && <p className="text-sm text-muted-foreground">Confirme apenas se você recebeu esse valor. Se o valor for diferente, rejeite o aviso e registre o recebimento correto.</p>}
    </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button variant="brand" className="mt-auto" type="submit" disabled={busy}>{busy ? "Registrando…" : action === "confirm" ? "Confirmar recebimento" : action === "refund" ? "Registrar devolução" : action === "report" ? "Enviar para conferência" : "Confirmar recebimento"}</Button></form>
  </SheetContent></Sheet>
}
