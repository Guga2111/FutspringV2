import { useState } from "react"
import { useFieldArray, useWatch } from "react-hook-form"
import type { DailyListItem } from "@/types/daily"
import type { PeladaMember } from "@/types/pelada"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { useCreateFinanceCollection } from "@/components/finance/hooks/useCreateFinanceCollection"
import { FinanceInput, FinanceSelect, FinanceFormError, FinanceCheck } from "@/components/finance/FinanceFields"
import { FinancePeoplePicker } from "@/components/finance/FinancePeoplePicker"
import { financeClassificationLabel } from "@/types/finance"
import { financeOptions, financeOptionsSummary, formatFinanceMoney, normalizeFinanceAmount, sumFinanceAmounts } from "@/utils/finance"
import { cn } from "@/lib/utils"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowRight01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { Field, FieldGroup, FieldLabel, FieldError } from "@/components/ui/field"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"

export function FinanceCreateForm({ peladaId, finance, members, dailies, onClose }: { peladaId: number; finance: FinanceController; members: PeladaMember[]; dailies: DailyListItem[]; onClose: () => void }) {
  const { form, step, setStep, next, template, save } = useCreateFinanceCollection(peladaId, finance, onClose)
  // controlled: only its own trigger closes it (toggling "Separar o valor em itens" must not); a template with items
  // and a validation error inside it open it
  const [optionsOpen, setOptionsOpen] = useState(false)
  const templates = (finance.overview?.templates ?? []).map((t, index) => ({ t, index })).filter(({ t }) => !t.archived)
  async function continueStep() {
    const valid = await next()
    const errors = form.formState.errors
    if (!valid && step === 0 && (errors.dueDate || errors.description || errors.dailyId || errors.components || errors.pixKey || errors.pixRecipient)) setOptionsOpen(true)
  }
  const values = useWatch({ control: form.control })
  const fields = useFieldArray({ control: form.control, name: "components" })
  const price = values.detailedPrice ? sumFinanceAmounts((values.components ?? []).map(c => c.amount ?? "")) : normalizeFinanceAmount(values.amount ?? "") ?? "0.00"
  const selected = members.filter(m => values.userIds?.includes(m.id))
  const guests = (values.guestNames ?? []).map(name => name.trim()).filter(Boolean)
  const count = selected.length + guests.length
  const total = sumFinanceAmounts(Array.from({ length: count }, () => price))
  const busy = finance.pending || form.formState.isSubmitting
  return <Sheet open onOpenChange={open => { if (!open && !busy) onClose() }}><SheetContent className="flex w-full flex-col gap-6 overflow-hidden sm:max-w-xl">
    <SheetHeader><SheetTitle>Nova cobrança</SheetTitle><SheetDescription>{["Defina o que será cobrado.", "Escolha quem deve pagar.", "Confira antes de publicar."][step]}</SheetDescription></SheetHeader>
    <ol aria-label="Etapas da cobrança" className="grid grid-cols-3 gap-3 text-sm">{["Cobrança", "Pessoas", "Revisão"].map((label, index) => <li key={label} aria-current={step === index ? "step" : undefined} className={cn("border-b-2 pb-2", step === index ? "border-brand font-medium text-foreground" : "border-border text-muted-foreground")}>{label}</li>)}</ol>
    <form noValidate className="flex min-h-0 flex-1 flex-col gap-4" onSubmit={event => { if (step < 2) { event.preventDefault(); void continueStep() } else { void form.handleSubmit(values => save(values, true))(event) } }}>
      {/* -m-1 p-1: room for the 4px focus ring (ring-2 + offset-2), which overflow-y-auto would clip */}
      <div className="-m-1 min-h-0 flex-1 overflow-y-auto p-1">
      {step === 0 && <FieldGroup>
        {templates.length > 0 && <Field><FieldLabel htmlFor="finance-create-template">Começar de um modelo</FieldLabel><Select onValueChange={value => { const item = finance.overview?.templates[Number(value)]; if (item) { template(item); if (item.components.length > 1) setOptionsOpen(true) } }}><SelectTrigger id="finance-create-template"><SelectValue placeholder="Opcional: escolha um modelo" /></SelectTrigger><SelectContent><SelectGroup>{templates.map(({ t, index }) => <SelectItem key={t.id ?? t.classification} value={String(index)}>{t.name}</SelectItem>)}</SelectGroup></SelectContent></Select></Field>}
        <FinanceInput control={form.control} name="name" label="O que você vai cobrar?" placeholder="Ex.: Diária de sábado ou Mensalidade de novembro" />
        <FinanceSelect control={form.control} name="classification" label="Tipo de cobrança" options={financeOptions(financeClassificationLabel)} />
        {values.detailedPrice ? <Field><FieldLabel>Valor por pessoa</FieldLabel><p className="text-2xl font-semibold tabular-nums">{formatFinanceMoney(price)}</p><p className="text-sm text-muted-foreground">Calculado pelos itens abaixo.</p></Field> : <FinanceInput control={form.control} name="amount" label="Valor por pessoa (R$)" placeholder="45,00" />}
        <Collapsible open={optionsOpen} onOpenChange={setOptionsOpen} className="rounded-tile bg-muted">
          <CollapsibleTrigger className="flex w-full items-center gap-3 rounded-tile px-4 py-3 text-left transition-colors hover:bg-row-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background">
            <span className="min-w-0 flex-1">
              <span className="block text-body font-semibold">Vencimento e outras opções</span>
              <span className="mt-0.5 block text-body-sm text-muted-foreground">{financeOptionsSummary({ ...values, componentCount: values.components?.length ?? 0 }, finance.overview?.settings)}</span>
            </span>
            <HugeiconsIcon icon={ArrowRight01Icon} className={cn("size-4 shrink-0 text-muted-foreground transition-transform duration-150", optionsOpen && "rotate-90")} />
          </CollapsibleTrigger>
          <CollapsibleContent className="px-4 pb-4 pt-2"><FieldGroup>
          <FinanceInput control={form.control} name="dueDate" label="Vencimento (opcional)" type="date" />
          <FinanceInput control={form.control} name="description" label="Observações para os jogadores" multiline />
          {dailies.length > 0 && <Field><FieldLabel htmlFor="finance-create-daily">Vincular a uma sessão (opcional)</FieldLabel><Select value={String(values.dailyId ?? "none")} onValueChange={value => form.setValue("dailyId", value === "none" ? null : Number(value))}><SelectTrigger id="finance-create-daily"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="none">Sem vínculo</SelectItem>{dailies.map(daily => <SelectItem key={daily.id} value={String(daily.id)}>{daily.dailyDate.split("-").reverse().join("/")}</SelectItem>)}</SelectGroup></SelectContent></Select></Field>}
          <FinanceCheck control={form.control} name="detailedPrice" label="Separar o valor em itens, como campo e juiz" />
          {values.detailedPrice && <FieldGroup>{fields.fields.map((item, index) => <FieldGroup key={item.id} className="rounded-lg bg-card p-3"><FinanceInput control={form.control} name={`components.${index}.name`} label="Item" /><FinanceInput control={form.control} name={`components.${index}.amount`} label="Valor (R$)" />{fields.fields.length > 1 && <Button type="button" variant="ghost" onClick={() => fields.remove(index)}>Remover item</Button>}</FieldGroup>)}<FieldError errors={[form.formState.errors.components]} />{fields.fields.length < 20 && <Button type="button" variant="outline" onClick={() => fields.append({ name: "", amount: "0.00" })}>Adicionar item</Button>}</FieldGroup>}
          <FinanceInput control={form.control} name="pixKey" label="Chave Pix para esta cobrança" /><FinanceInput control={form.control} name="pixRecipient" label="Quem recebe o Pix" />
        </FieldGroup></CollapsibleContent></Collapsible>
      </FieldGroup>}
      {step === 1 && <><FinancePeoplePicker peladaId={peladaId} members={members} dailies={dailies} selected={values.userIds ?? []} onSelect={ids => form.setValue("userIds", ids, { shouldDirty: true })} guests={values.guestNames ?? []} onGuests={names => form.setValue("guestNames", names, { shouldDirty: true })} />
        <FieldError errors={[form.formState.errors.userIds, form.formState.errors.guestNames]} />
      </>}
      {step === 2 && <div className="flex flex-col gap-5"><div><h3 className="text-lg font-semibold">{values.name}</h3><p className="mt-1 text-sm text-muted-foreground">{financeClassificationLabel[values.classification ?? "DAILY"]}{values.dueDate ? ` · Vence ${values.dueDate.split("-").reverse().join("/")}` : " · Sem vencimento"}</p></div>
        <dl className="flex flex-col gap-3 rounded-lg bg-muted p-4"><div className="flex justify-between gap-3"><dt>Por pessoa</dt><dd className="font-semibold tabular-nums">{formatFinanceMoney(price)}</dd></div><div className="flex justify-between gap-3"><dt>Pessoas</dt><dd>{count}</dd></div><div className="flex justify-between gap-3"><dt>Total a receber</dt><dd className="font-semibold tabular-nums">{formatFinanceMoney(total)}</dd></div></dl>
        <div><h4 className="mb-2 text-sm font-medium">Quem será cobrado</h4><ul className="max-h-48 overflow-y-auto text-sm">{selected.map(m => <li className="py-1.5" key={m.id}>{m.username}</li>)}{guests.map((name, index) => <li className="py-1.5" key={`${index}:${name}`}>{name} · Convidado</li>)}{count === 0 && <li className="text-muted-foreground">Nenhuma pessoa selecionada. Você pode salvar como rascunho.</li>}</ul></div>
        {values.pixKey && <p className="break-all text-sm text-muted-foreground">Pix: {values.pixKey}{values.pixRecipient ? ` · ${values.pixRecipient}` : ""}</p>}
        <p className="text-sm text-muted-foreground">Ao publicar, os jogadores poderão consultar suas cobranças e informar pagamentos.</p>
      </div>}
      </div>
      <FinanceFormError message={form.formState.errors.root?.message} />
      <div className="flex shrink-0 flex-wrap justify-between gap-3 pt-3">{step > 0 ? <Button type="button" variant="ghost" disabled={busy} onClick={() => setStep(step - 1)}>Voltar</Button> : <Button type="button" variant="ghost" disabled={busy} onClick={onClose}>Cancelar</Button>}
        {step < 2 ? <Button variant="brand" type="button" disabled={busy} onClick={() => { void continueStep() }}>Continuar</Button> : <div className="flex flex-wrap gap-2"><Button type="button" variant="outline" disabled={busy} onClick={() => { void form.handleSubmit(values => save(values, false))() }}>Salvar rascunho</Button><Button variant="brand" type="submit" disabled={busy || count === 0}>{busy ? "Salvando…" : "Criar e publicar"}</Button></div>}
      </div>
    </form>
  </SheetContent></Sheet>
}
