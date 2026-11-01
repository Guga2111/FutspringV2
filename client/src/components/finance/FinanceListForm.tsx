import { useFieldArray, useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import * as api from "@/api/finance"
import { financeListFormSchema, type FinanceListValues } from "@/schemas/finance"
import type { FinanceList, FinanceTemplate } from "@/types/finance"
import { financeClassificationLabel } from "@/types/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceSelect, FinanceFormError } from "@/components/finance/FinanceFields"
import { financeOptions } from "@/utils/finance"
import { Button } from "@/components/ui/button"
import { FieldGroup, Field, FieldLabel } from "@/components/ui/field"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { DailyListItem } from "@/types/daily"
import { applyServerErrors } from "@/lib/form-errors"

export function FinanceListForm({ peladaId, finance, list, template, asTemplate = Boolean(template), dailies, onClose }: {
  peladaId: number; finance: FinanceController; list?: FinanceList; template?: FinanceTemplate; asTemplate?: boolean; dailies: DailyListItem[]; onClose: () => void
}) {
  const source = list ?? template
  const defaults: FinanceListValues = {
    name: source?.name ?? "", classification: source?.classification ?? "DAILY", description: source?.description ?? "",
    dueDate: list?.dueDate ?? "", dailyId: list?.dailyId ?? null,
    components: source?.components ?? [{ name: "Campo", amount: "0.00" }], pixKey: source?.pixKey ?? finance.overview?.settings.pixKey ?? "",
    pixRecipient: source?.pixRecipient ?? finance.overview?.settings.pixRecipient ?? "", qrId: source?.qrId ?? finance.overview?.settings.qrId ?? null, reason: "",
  }
  const form = useForm<FinanceListValues>({ resolver: zodResolver(financeListFormSchema), defaultValues: defaults })
  const fields = useFieldArray({ control: form.control, name: "components" })
  const published = Boolean(list?.publishedAt)
  async function submit(values: FinanceListValues) {
    try {
      await finance.mutate(JSON.stringify({ type: asTemplate ? "template" : "list", id: source?.id, values }), values.reason, command => asTemplate
        ? api.saveFinanceTemplate(peladaId, command, { ...values, archived: template?.archived ?? false }, template?.id ?? null)
        : api.saveFinanceList(peladaId, command, values, list?.id ?? null))
      toast.success(asTemplate ? "Modelo salvo" : "Lista salva")
      onClose()
    } catch (e) { applyServerErrors(form, e, "Não foi possível salvar") }
  }
  const qrIdValue = useWatch({ control: form.control, name: "qrId" })
  const dailyIdValue = useWatch({ control: form.control, name: "dailyId" })
  return <Sheet open onOpenChange={open => { if (!open && !form.formState.isSubmitting) onClose() }}><SheetContent className="flex w-full flex-col gap-5 overflow-y-auto sm:max-w-xl">
    <SheetHeader><SheetTitle>{asTemplate ? template ? "Editar modelo" : "Novo modelo" : "Editar cobrança"}</SheetTitle><SheetDescription>Os totais são calculados a partir dos valores por pessoa.</SheetDescription></SheetHeader>
    <form onSubmit={form.handleSubmit(submit)} noValidate className="flex flex-col gap-5">
      <FieldGroup>
        {!source && !asTemplate && <Field><FieldLabel htmlFor="finance-template">Começar por um modelo</FieldLabel><Select onValueChange={value => {
          const selected = finance.overview?.templates[Number(value)]
          if (selected) form.reset({ ...defaults, ...selected, description: selected.description ?? "", pixKey: selected.pixKey ?? "", pixRecipient: selected.pixRecipient ?? "" })
        }}><SelectTrigger id="finance-template"><SelectValue placeholder="Personalizado" /></SelectTrigger><SelectContent><SelectGroup>{finance.overview?.templates.map((t, i) => !t.archived && <SelectItem key={t.id ?? t.classification} value={String(i)}>{t.name}</SelectItem>)}</SelectGroup></SelectContent></Select></Field>}
        <FinanceInput control={form.control} name="name" label="Nome" />
        <FinanceSelect control={form.control} name="classification" label="Classificação" options={financeOptions(financeClassificationLabel)} disabled={published} />
        <FinanceInput control={form.control} name="description" label="Finalidade e condições" multiline />
        {!asTemplate && <><FinanceInput control={form.control} name="dueDate" label="Vencimento (opcional)" type="date" />
          <Field><FieldLabel htmlFor="finance-daily">Diária vinculada (opcional)</FieldLabel><Select value={String(dailyIdValue ?? "none")} onValueChange={v => form.setValue("dailyId", v === "none" ? null : Number(v), { shouldDirty: true })}>
            <SelectTrigger id="finance-daily"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="none">Sem vínculo</SelectItem>{dailies.map(d => <SelectItem key={d.id} value={String(d.id)}>{d.dailyDate}</SelectItem>)}</SelectGroup></SelectContent></Select></Field></>}
        {fields.fields.map((field, i) => <FieldGroup key={field.id} className="rounded-md bg-muted p-3">
          <FinanceInput control={form.control} name={`components.${i}.name`} label="Componente" disabled={published} />
          <FinanceInput control={form.control} name={`components.${i}.amount`} label="Valor (R$)" disabled={published} />
          {!published && fields.fields.length > 1 && <Button type="button" variant="ghost" onClick={() => fields.remove(i)}>Remover componente</Button>}
        </FieldGroup>)}
        {!published && fields.fields.length < 20 && <Button type="button" variant="outline" onClick={() => fields.append({ name: "", amount: "0.00" })}>Adicionar componente</Button>}
        <FinanceInput control={form.control} name="pixKey" label="Chave Pix" />
        <FinanceInput control={form.control} name="pixRecipient" label="Recebedor" />
        {list && qrIdValue !== null && <Button variant="ghost" type="button" onClick={() => form.setValue("qrId", null, { shouldDirty: true })}>Remover QR Code desta lista</Button>}
        {published && <FinanceInput control={form.control} name="reason" label="Motivo da alteração" multiline />}
      </FieldGroup>
      <FinanceFormError message={form.formState.errors.root?.message} />
      <Button type="submit" disabled={finance.pending}>{finance.pending ? "Salvando…" : asTemplate ? "Salvar modelo" : "Salvar cobrança"}</Button>
    </form>
  </SheetContent></Sheet>
}
