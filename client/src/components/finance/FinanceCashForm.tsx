import { useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { cashFinance } from "@/api/finance"
import { financeCashSchema, type FinanceCashValues } from "@/schemas/finance"
import { financeMethodLabel } from "@/types/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceSelect, FinanceFormError } from "@/components/finance/FinanceFields"
import { financeOptions } from "@/utils/finance"
import { FieldGroup, Field, FieldLabel } from "@/components/ui/field"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { DailyListItem } from "@/types/daily"
import { applyServerErrors } from "@/lib/form-errors"

export function FinanceCashForm({ peladaId, finance, dailies, onClose }: { peladaId: number; finance: FinanceController; dailies: DailyListItem[]; onClose: () => void }) {
  const form = useForm<FinanceCashValues>({ resolver: zodResolver(financeCashSchema), defaultValues: { kind: "EXPENSE", amount: "0.00", description: "", category: "", effectiveDate: "", method: "PIX", listId: finance.selected, dailyId: null, reason: "" } })
  const listIdValue = useWatch({ control: form.control, name: "listId" })
  const dailyIdValue = useWatch({ control: form.control, name: "dailyId" })
  return <Sheet open onOpenChange={open => { if (!open && !finance.pending) onClose() }}><SheetContent className="flex w-full flex-col gap-5 overflow-y-auto sm:max-w-lg">
    <SheetHeader><SheetTitle>Registrar movimentação</SheetTitle><SheetDescription>Registre dinheiro que já entrou ou saiu. Cobranças não pagas ficam fora do caixa.</SheetDescription></SheetHeader>
    <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(async values => {
      try { await finance.mutate(JSON.stringify({ type: "cash", values }), values.reason, command => cashFinance(peladaId, command, values)); toast.success("Movimentação registrada"); onClose() }
      catch (e) { applyServerErrors(form, e, "Não foi possível registrar a movimentação") }
    })}><FieldGroup>
      <FinanceSelect control={form.control} name="kind" label="Tipo" options={[{ value: "EXPENSE", label: "Despesa realizada" }, { value: "INCOME", label: "Outra entrada" }, { value: "OPENING", label: "Saldo inicial" }]} />
      <FinanceInput control={form.control} name="description" label="Descrição" /><FinanceInput control={form.control} name="category" label="Categoria (obrigatória para despesa)" />
      <FinanceInput control={form.control} name="amount" label="Valor (R$)" /><FinanceInput control={form.control} name="effectiveDate" label="Data efetiva (hoje se não informar)" type="date" />
      <FinanceSelect control={form.control} name="method" label="Meio de pagamento" options={financeOptions(financeMethodLabel)} />
      <Field><FieldLabel htmlFor="finance-cash-list">Lista vinculada</FieldLabel><Select value={String(listIdValue ?? "none")} onValueChange={v => form.setValue("listId", v === "none" ? null : Number(v))}><SelectTrigger id="finance-cash-list"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="none">Sem vínculo</SelectItem>{finance.overview?.lists.map(l => <SelectItem key={l.id} value={String(l.id)}>{l.name}</SelectItem>)}</SelectGroup></SelectContent></Select></Field>
      <Field><FieldLabel htmlFor="finance-cash-daily">Diária vinculada</FieldLabel><Select value={String(dailyIdValue ?? "none")} onValueChange={v => form.setValue("dailyId", v === "none" ? null : Number(v))}><SelectTrigger id="finance-cash-daily"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="none">Sem vínculo</SelectItem>{dailies.map(d => <SelectItem key={d.id} value={String(d.id)}>{d.dailyDate}</SelectItem>)}</SelectGroup></SelectContent></Select></Field>
      <FinanceInput control={form.control} name="reason" label="Motivo (obrigatório para saldo inicial)" multiline />
    </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button type="submit" disabled={finance.pending}>Registrar movimentação</Button></form>
  </SheetContent></Sheet>
}
