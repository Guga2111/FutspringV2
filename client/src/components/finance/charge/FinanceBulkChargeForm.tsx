import { useMemo } from "react"
import { useForm, useFieldArray, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { adjustFinanceCharges } from "@/api/finance"
import { makeFinanceBulkAdjustSchema, type FinanceBulkAdjustInput, type FinanceBulkAdjustValues } from "@/schemas/finance"
import type { FinanceCharge, FinanceList } from "@/types/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceFormError } from "@/components/finance/FinanceFields"
import { bulkAdjustSummary, formatFinanceMoney } from "@/utils/finance"
import { FieldGroup } from "@/components/ui/field"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { applyServerErrors } from "@/lib/form-errors"

const participants = (count: number) => (count === 1 ? "1 participante" : `${count} participantes`)

// "Ajustar valor" of the selection: one composition (prefilled with the list's) for every selected charge, each
// keeping its due date. The server changes everyone or nobody; onAdjusted runs only on success (leaves selection mode)
export function FinanceBulkChargeForm({ peladaId, finance, list, charges, onAdjusted, onClose }: {
  peladaId: number; finance: FinanceController; list: FinanceList; charges: FinanceCharge[]; onAdjusted: () => void; onClose: () => void
}) {
  const published = list.publishedAt !== null
  const schema = useMemo(() => makeFinanceBulkAdjustSchema(published), [published])
  const form = useForm<FinanceBulkAdjustInput, unknown, FinanceBulkAdjustValues>({
    resolver: zodResolver(schema),
    defaultValues: { components: list.components.map(c => ({ name: c.name, amount: c.amount })), adjustment: "0.00", reason: "" },
  })
  const fields = useFieldArray({ control: form.control, name: "components" })
  const components = useWatch({ control: form.control, name: "components" })
  const adjustment = useWatch({ control: form.control, name: "adjustment" })
  const summary = bulkAdjustSummary({ components, adjustment }, charges, list)
  const ids = charges.map(c => c.id)
  const busy = finance.pending || form.formState.isSubmitting
  async function submit(values: FinanceBulkAdjustValues) {
    try {
      await finance.mutate(JSON.stringify({ bulkAdjust: list.id, ids, values }), values.reason, command => adjustFinanceCharges(peladaId, list.id, command, ids, values))
      toast.success(charges.length === 1 ? "Valor de 1 participante ajustado" : `Valor de ${charges.length} participantes ajustado`)
      onAdjusted()
      onClose()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível ajustar os valores")
    }
  }
  return <Sheet open onOpenChange={open => { if (!open && !busy) onClose() }}><SheetContent className="flex w-full flex-col gap-5 overflow-y-auto sm:max-w-lg">
    <SheetHeader>
      <SheetTitle>Ajustar valor de {participants(charges.length)}</SheetTitle>
      <SheetDescription>Todos passam a ter esta composição. Cada um mantém o próprio vencimento; um abatimento reduz a obrigação sem criar entrada no caixa.</SheetDescription>
    </SheetHeader>
    <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(submit)}><FieldGroup>
      {fields.fields.map((item, i) => <FieldGroup key={item.id} className="rounded-md bg-muted p-3"><FinanceInput control={form.control} name={`components.${i}.name`} label="Componente" /><FinanceInput control={form.control} name={`components.${i}.amount`} label="Valor (R$)" />
        {fields.fields.length > 1 && <Button variant="ghost" type="button" onClick={() => fields.remove(i)}>Remover componente</Button>}</FieldGroup>)}
      {fields.fields.length < 20 && <Button variant="outline" type="button" onClick={() => fields.append({ name: "", amount: "0.00" })}>Adicionar componente</Button>}
      <FinanceInput control={form.control} name="adjustment" label="Ajuste (R$; negativo para abatimento)" />
      <div className="flex flex-col gap-1 rounded-md border p-3 text-sm" aria-live="polite">
        <p className="font-medium">{summary.each === null ? "Confira os valores" : `Cada um passa a dever ${formatFinanceMoney(summary.each)} · total ${formatFinanceMoney(summary.total ?? "0.00")}`}</p>
        {summary.differs > 0 && <p className="text-muted-foreground">{summary.differs === 1 ? "1 tinha um valor próprio e passa a ter este." : `${summary.differs} tinham um valor próprio e passam a ter este.`}</p>}
      </div>
      <FinanceInput control={form.control} name="reason" label={published ? "Motivo obrigatório" : "Motivo (opcional no rascunho)"} multiline />
    </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button variant="brand" type="submit" disabled={busy}>{busy ? "Ajustando…" : `Ajustar ${participants(charges.length)}`}</Button></form>
  </SheetContent></Sheet>
}
