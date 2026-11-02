import { useState } from "react"
import { useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { cashFinance } from "@/api/finance"
import { financeCashFormSchema, type FinanceCashValues } from "@/schemas/finance"
import { financeMethodLabel } from "@/types/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceSelect, FinanceFormError } from "@/components/finance/FinanceFields"
import { financeOptions } from "@/utils/finance"
import { FieldGroup, Field, FieldLabel } from "@/components/ui/field"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import type { DailyListItem } from "@/types/daily"
import { applyServerErrors } from "@/lib/form-errors"

export function FinanceCashForm({ peladaId, finance, kind, dailies, onClose }: { peladaId: number; finance: FinanceController; kind: "EXPENSE" | "INCOME" | "OPENING"; dailies: DailyListItem[]; onClose: () => void }) {
  const [options, setOptions] = useState(false)
  const form = useForm<FinanceCashValues>({ resolver: zodResolver(financeCashFormSchema), defaultValues: { kind, amount: "", description: kind === "OPENING" ? "Saldo inicial" : "", category: "", effectiveDate: "", method: "PIX", listId: null, dailyId: null, reason: "" } })
  const listId = useWatch({ control: form.control, name: "listId" })
  const dailyId = useWatch({ control: form.control, name: "dailyId" })
  const date = useWatch({ control: form.control, name: "effectiveDate" })
  const method = useWatch({ control: form.control, name: "method" })
  const title = kind === "EXPENSE" ? "Adicionar despesa" : kind === "INCOME" ? "Registrar outra entrada" : "Registrar saldo inicial"
  const busy = finance.pending || form.formState.isSubmitting
  return <Sheet open onOpenChange={open => { if (!open && !busy) onClose() }}><SheetContent className="flex w-full flex-col gap-6 overflow-y-auto sm:max-w-lg">
    <SheetHeader><SheetTitle>{title}</SheetTitle><SheetDescription>{kind === "EXPENSE" ? "Um gasto que a pelada já pagou." : kind === "INCOME" ? "Dinheiro recebido fora das cobranças dos jogadores." : "O valor que a pelada já tinha antes deste controle."}</SheetDescription></SheetHeader>
    <form noValidate className="flex flex-1 flex-col gap-6" onSubmit={form.handleSubmit(async values => {
      try { await finance.mutate(JSON.stringify({ type: "cash", values }), values.reason, command => cashFinance(peladaId, command, values)); toast.success("Caixa atualizado"); onClose() }
      catch (error) { applyServerErrors(form, error, "Não foi possível registrar a movimentação") }
    })}><FieldGroup>
      <FinanceInput control={form.control} name="description" label={kind === "EXPENSE" ? "O que foi pago?" : "Descrição"} placeholder={kind === "EXPENSE" ? "Ex.: Aluguel do campo" : "Ex.: Contribuição para o campeonato"} />
      <FinanceInput control={form.control} name="amount" label="Valor (R$)" placeholder="150,00" />
      {kind === "EXPENSE" && <FinanceInput control={form.control} name="category" label="Categoria" placeholder="Ex.: Campo, juiz ou material" />}
      {kind === "OPENING" && <FinanceInput control={form.control} name="reason" label="Origem deste saldo" multiline />}
      <Collapsible open={options} onOpenChange={setOptions}><div className="flex items-center justify-between gap-3"><p className="text-sm">{date ? date.split("-").reverse().join("/") : "Hoje"} · {financeMethodLabel[method]}</p><CollapsibleTrigger asChild><Button type="button" variant="ghost" size="sm">{options ? "Recolher" : "Mais opções"}</Button></CollapsibleTrigger></div><CollapsibleContent className="pt-4"><FieldGroup>
        <FinanceInput control={form.control} name="effectiveDate" label="Data em que o dinheiro entrou ou saiu" type="date" />
        <FinanceSelect control={form.control} name="method" label="Meio de pagamento" options={financeOptions(financeMethodLabel)} />
        {kind !== "OPENING" && <><Field><FieldLabel htmlFor="finance-cash-list">Vincular a uma cobrança (opcional)</FieldLabel><Select value={String(listId ?? "none")} onValueChange={value => form.setValue("listId", value === "none" ? null : Number(value))}><SelectTrigger id="finance-cash-list"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="none">Sem vínculo</SelectItem>{finance.overview?.lists.map(list => <SelectItem key={list.id} value={String(list.id)}>{list.name}</SelectItem>)}</SelectGroup></SelectContent></Select></Field>
          <Field><FieldLabel htmlFor="finance-cash-daily">Vincular a uma sessão (opcional)</FieldLabel><Select value={String(dailyId ?? "none")} onValueChange={value => form.setValue("dailyId", value === "none" ? null : Number(value))}><SelectTrigger id="finance-cash-daily"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="none">Sem vínculo</SelectItem>{dailies.map(daily => <SelectItem key={daily.id} value={String(daily.id)}>{daily.dailyDate.split("-").reverse().join("/")}</SelectItem>)}</SelectGroup></SelectContent></Select></Field></>}
      </FieldGroup></CollapsibleContent></Collapsible>
    </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button variant="gradient" className="mt-auto" type="submit" disabled={busy}>{busy ? "Salvando…" : title}</Button></form>
  </SheetContent></Sheet>
}
