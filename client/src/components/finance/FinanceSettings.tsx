import { FinanceImage } from "@/components/finance/FinanceImage"
import { Controller, useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import * as api from "@/api/finance"
import { financeSettingsSchema, financeManagersSchema, financeQrSchema } from "@/schemas/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import type { PeladaMember } from "@/types/pelada"
import { FinanceInput, FinanceSelect, FinanceFormError } from "@/components/finance/FinanceFields"
import { Field, FieldGroup, FieldLabel, FieldError, FieldDescription } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Checkbox } from "@/components/ui/checkbox"
import { Button } from "@/components/ui/button"
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card"
import { applyServerErrors } from "@/lib/form-errors"

export function FinanceSettings({ peladaId, finance, members }: { peladaId: number; finance: FinanceController; members: PeladaMember[] }) {
  const settings = finance.overview?.settings
  const form = useForm({ resolver: zodResolver(financeSettingsSchema), defaultValues: { visibility: settings?.visibility ?? "INDIVIDUAL", pixKey: settings?.pixKey ?? "", pixRecipient: settings?.pixRecipient ?? "", qrId: settings?.qrId ?? null } })
  const managerForm = useForm({ resolver: zodResolver(financeManagersSchema), defaultValues: { userIds: settings?.managerIds ?? [] } })
  const qrId = useWatch({ control: form.control, name: "qrId" })
  return <div className="grid min-w-0 gap-5 md:grid-cols-2">
    <Card><CardHeader><CardTitle>Visibilidade e Pix</CardTitle><CardDescription>O padrão é usado em novas listas. Listas existentes preservam suas próprias instruções.</CardDescription></CardHeader><CardContent>
      <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(async values => {
        try { await finance.mutate(JSON.stringify({ type: "settings", values }), "", command => api.saveFinanceSettings(peladaId, command, values)); form.reset(values); toast.success("Configuração salva") }
        catch (e) { applyServerErrors(form, e, "Não foi possível salvar a configuração") }
      })}><FieldGroup><FinanceSelect control={form.control} name="visibility" label="Visibilidade" options={[{ value: "INDIVIDUAL", label: "Individual: cada membro vê suas cobranças" }, { value: "TRANSPARENT", label: "Transparente: membros veem listas e caixa" }]} />
        <FinanceInput control={form.control} name="pixKey" label="Chave Pix padrão" /><FinanceInput control={form.control} name="pixRecipient" label="Recebedor padrão" />
        {qrId !== null && <div className="flex flex-wrap gap-2"><FinanceImage peladaId={peladaId} kind="qr" imageId={qrId} /><Button variant="ghost" type="button" onClick={() => form.setValue("qrId", null, { shouldDirty: true })}>Remover QR Code padrão</Button></div>}
      </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button variant="brand" type="submit" disabled={finance.pending || !form.formState.isDirty}>Salvar configuração</Button></form>
    </CardContent></Card>
    <FinanceQrForm peladaId={peladaId} finance={finance} listId={null} />
    {finance.overview?.permissions.appointManagers && <Card className="md:col-span-2"><CardHeader><CardTitle>Responsáveis financeiros</CardTitle><CardDescription>Administradores e criador já têm acesso. Os responsáveis ganham apenas os poderes do financeiro.</CardDescription></CardHeader><CardContent>
      <form noValidate className="flex flex-col gap-5" onSubmit={managerForm.handleSubmit(async values => {
        try { await finance.mutate(JSON.stringify({ type: "managers", values }), "", command => api.saveFinanceManagers(peladaId, command, values.userIds)); managerForm.reset(values); toast.success("Responsáveis atualizados") }
        catch (e) { applyServerErrors(managerForm, e, "Não foi possível salvar os responsáveis") }
      })}><Controller control={managerForm.control} name="userIds" render={({ field, fieldState }) => <FieldGroup>{members.map(member => <Field key={member.id} orientation="horizontal"><Checkbox id={`manager-${member.id}`} checked={field.value.includes(member.id)} onCheckedChange={checked => field.onChange(checked ? [...field.value, member.id] : field.value.filter(id => id !== member.id))} /><FieldLabel htmlFor={`manager-${member.id}`}>{member.username}</FieldLabel></Field>)}<FieldError errors={[fieldState.error]} /></FieldGroup>} />
        <FinanceFormError message={managerForm.formState.errors.root?.message} /><Button variant="brand" type="submit" disabled={finance.pending || !managerForm.formState.isDirty}>Salvar responsáveis</Button>
      </form>
    </CardContent></Card>}
  </div>
}
export function FinanceQrForm({ peladaId, finance, listId }: { peladaId: number; finance: FinanceController; listId: number | null }) {
  const form = useForm({ resolver: zodResolver(financeQrSchema), defaultValues: { reason: "" } })
  return <Card><CardHeader><CardTitle>QR Code {listId === null ? "padrão" : "da lista"}</CardTitle><CardDescription>Envie a imagem fornecida pelo banco. Ela acompanha as instruções Pix, sem prazo de expiração.</CardDescription></CardHeader><CardContent>
    <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(async values => {
      try { await finance.mutate(JSON.stringify({ type: "qr", listId, reason: values.reason, file: { name: values.file.name, size: values.file.size, modified: values.file.lastModified } }), values.reason, command => api.uploadFinanceQr(peladaId, listId, command, values.file)); toast.success("QR Code salvo"); form.reset() }
      catch (e) { applyServerErrors(form, e, "Não foi possível enviar o QR Code") }
    })}><FieldGroup><Controller control={form.control} name="file" render={({ field, fieldState }) => <Field data-invalid={fieldState.invalid}><FieldLabel htmlFor="finance-qr-file">Imagem do QR Code</FieldLabel><Input id="finance-qr-file" type="file" accept="image/jpeg,image/png" onChange={e => field.onChange(e.target.files?.[0])} aria-invalid={fieldState.invalid} /><FieldDescription>JPEG ou PNG, até 5 MB.</FieldDescription><FieldError errors={[fieldState.error]} /></Field>} />
      {listId !== null && <FinanceInput control={form.control} name="reason" label="Motivo (obrigatório após publicação)" multiline />}
    </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button type="submit" variant="outline" disabled={finance.pending}>Salvar QR Code</Button></form>
  </CardContent></Card>
}
