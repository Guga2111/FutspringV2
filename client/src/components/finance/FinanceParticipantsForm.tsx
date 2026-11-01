import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useState } from "react"
import { toast } from "sonner"
import { addFinanceParticipants } from "@/api/finance"
import { financeParticipantsFormSchema } from "@/schemas/finance"
import type { FinanceDetail } from "@/types/finance"
import type { PeladaMember } from "@/types/pelada"
import type { DailyListItem } from "@/types/daily"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { useFinanceCandidates } from "@/components/finance/hooks/useFinanceCandidates"
import { FinanceInput, FinanceFormError } from "@/components/finance/FinanceFields"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { FieldGroup, Field, FieldLabel, FieldError, FieldSet, FieldLegend } from "@/components/ui/field"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { applyServerErrors } from "@/lib/form-errors"

export function FinanceParticipantsForm({ peladaId, finance, detail, members, dailies, onClose }: {
  peladaId: number; finance: FinanceController; detail: FinanceDetail; members: PeladaMember[]; dailies: DailyListItem[]; onClose: () => void
}) {
  const form = useForm({ resolver: zodResolver(financeParticipantsFormSchema), defaultValues: { userIds: [] as number[], guestName: "", reason: "" } })
  const [search, setSearch] = useState("")
  const [filter, setFilter] = useState("all")
  const [dailyId, setDailyId] = useState<number | null>(null)
  const candidates = useFinanceCandidates(peladaId, dailyId)
  const existing = new Set(detail.charges.filter(c => !c.canceled).map(c => c.userId))
  const rows = members.filter(m => !existing.has(m.id) && m.username.toLocaleLowerCase("pt-BR").includes(search.toLocaleLowerCase("pt-BR")) && (filter === "all" || (filter === "monthly") === m.isMensalista))
  return <Sheet open onOpenChange={open => { if (!open && !finance.pending) onClose() }}><SheetContent className="flex w-full flex-col gap-5 overflow-y-auto sm:max-w-xl">
    <SheetHeader><SheetTitle>Adicionar participantes</SheetTitle><SheetDescription>Revise a seleção antes de salvar. Presença confirmada não comprova participação efetiva.</SheetDescription></SheetHeader>
    <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(async values => {
      try { await finance.mutate(JSON.stringify({ type: "participants", listId: detail.collection.id, values }), values.reason, command => addFinanceParticipants(peladaId, detail.collection.id, command, { userIds: values.userIds, guestNames: values.guestName ? [values.guestName] : [], reason: values.reason })); toast.success("Participantes adicionados"); onClose() }
      catch (e) { applyServerErrors(form, e, "Não foi possível adicionar participantes") }
    })}><FieldGroup>
      <Field><FieldLabel htmlFor="finance-import">Importar de uma diária</FieldLabel><Select value={String(dailyId ?? "none")} onValueChange={v => setDailyId(v === "none" ? null : Number(v))}>
        <SelectTrigger id="finance-import"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="none">Não importar</SelectItem>{dailies.map(d => <SelectItem key={d.id} value={String(d.id)}>{d.dailyDate}</SelectItem>)}</SelectGroup></SelectContent></Select>
        {dailyId !== null && <Button type="button" variant="outline" disabled={candidates.loading || Boolean(candidates.error)} onClick={() => form.setValue("userIds", candidates.rows.filter(c => !existing.has(c.userId) && members.some(m => m.id === c.userId)).map(c => c.userId), { shouldDirty: true })}>{candidates.loading ? "Carregando…" : `Selecionar ${candidates.rows.length} confirmados para revisão`}</Button>}
        <FinanceFormError message={candidates.error ?? undefined} />
      </Field>
      <Field><FieldLabel htmlFor="finance-member-search">Buscar membro</FieldLabel><Input id="finance-member-search" value={search} onChange={e => setSearch(e.target.value)} /></Field>
      <Field><FieldLabel htmlFor="finance-member-type">Tipo de membro</FieldLabel><Select value={filter} onValueChange={setFilter}><SelectTrigger id="finance-member-type"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="all">Todos</SelectItem><SelectItem value="monthly">Mensalistas</SelectItem><SelectItem value="daily">Diaristas</SelectItem></SelectGroup></SelectContent></Select></Field>
      <Controller control={form.control} name="userIds" render={({ field, fieldState }) => <FieldSet><FieldLegend>Membros · {field.value.length} selecionados</FieldLegend>
        <FieldGroup>{rows.map(member => <Field key={member.id} orientation="horizontal"><Checkbox id={`participant-${member.id}`} checked={field.value.includes(member.id)} onCheckedChange={checked => field.onChange(checked ? [...field.value, member.id] : field.value.filter(id => id !== member.id))} /><FieldLabel htmlFor={`participant-${member.id}`}>{member.username} · {member.isMensalista ? "Mensalista" : "Diarista"}</FieldLabel></Field>)}</FieldGroup><FieldError errors={[fieldState.error]} />
        {rows.length === 0 && <p className="text-sm text-muted-foreground">Nenhum membro disponível neste filtro.</p>}
      </FieldSet>} />
      <FinanceInput control={form.control} name="guestName" label="Adicionar convidado por nome (opcional)" />
      <p className="text-sm text-muted-foreground">Nomes iguais criam convidados diferentes. Nenhuma conta é vinculada automaticamente.</p>
      {detail.collection.publishedAt && <FinanceInput control={form.control} name="reason" label="Motivo da inclusão após publicação" multiline />}
    </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button type="submit" disabled={finance.pending}>Adicionar à lista</Button></form>
  </SheetContent></Sheet>
}
