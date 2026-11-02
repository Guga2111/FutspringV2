import { useForm, useFieldArray } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { saveFinanceCharge } from "@/api/finance"
import { financeChargeFormSchema, type FinanceChargeValues } from "@/schemas/finance"
import type { FinanceCharge, FinanceList } from "@/types/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceFormError } from "@/components/finance/FinanceFields"
import { FieldGroup } from "@/components/ui/field"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { applyServerErrors } from "@/lib/form-errors"

export function FinanceChargeForm({ peladaId, finance, list, charge, onClose }: { peladaId: number; finance: FinanceController; list: FinanceList; charge: FinanceCharge; onClose: () => void }) {
  const form = useForm<FinanceChargeValues>({ resolver: zodResolver(financeChargeFormSchema), defaultValues: { components: charge.components, adjustment: charge.adjustment, dueDate: charge.dueDate ?? "", reason: "" } })
  const fields = useFieldArray({ control: form.control, name: "components" })
  return <Sheet open onOpenChange={open => { if (!open && !finance.pending) onClose() }}><SheetContent className="flex w-full flex-col gap-5 overflow-y-auto sm:max-w-lg">
    <SheetHeader><SheetTitle>Ajustar cobrança</SheetTitle><SheetDescription>{charge.name}. Um abatimento reduz a obrigação, sem criar entrada no caixa.</SheetDescription></SheetHeader>
    <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(async values => {
      try { await finance.mutate(JSON.stringify({ action: "adjust", id: charge.id, values }), values.reason, command => saveFinanceCharge(peladaId, list.id, charge.id, command, values)); toast.success("Cobrança ajustada"); onClose() }
      catch (e) { applyServerErrors(form, e, "Não foi possível ajustar a cobrança") }
    })}><FieldGroup>
      {fields.fields.map((item, i) => <FieldGroup key={item.id} className="rounded-md bg-muted p-3"><FinanceInput control={form.control} name={`components.${i}.name`} label="Componente" /><FinanceInput control={form.control} name={`components.${i}.amount`} label="Valor (R$)" />
        {fields.fields.length > 1 && <Button variant="ghost" type="button" onClick={() => fields.remove(i)}>Remover componente</Button>}</FieldGroup>)}
      {fields.fields.length < 20 && <Button variant="outline" type="button" onClick={() => fields.append({ name: "", amount: "0.00" })}>Adicionar componente</Button>}
      <FinanceInput control={form.control} name="adjustment" label="Ajuste (R$; negativo para abatimento)" />
      <FinanceInput control={form.control} name="dueDate" label="Vencimento" type="date" />
      <FinanceInput control={form.control} name="reason" label={list.publishedAt ? "Motivo obrigatório" : "Motivo (opcional no rascunho)"} multiline />
    </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button variant="brand" type="submit" disabled={finance.pending}>Salvar ajuste</Button></form>
  </SheetContent></Sheet>
}
