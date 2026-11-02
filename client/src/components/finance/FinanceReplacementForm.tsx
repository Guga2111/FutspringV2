import { useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { financeReplacementSchema } from "@/schemas/finance"
import { replaceFinanceCharge } from "@/api/finance"
import type { FinanceCharge, FinanceList } from "@/types/finance"
import type { PeladaMember } from "@/types/pelada"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceFormError } from "@/components/finance/FinanceFields"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Button } from "@/components/ui/button"
import { applyServerErrors } from "@/lib/form-errors"
import { toast } from "sonner"
export function FinanceReplacementForm({ peladaId, finance, charge, list, members, onClose }: { peladaId: number; finance: FinanceController; charge: FinanceCharge; list: FinanceList; members: PeladaMember[]; onClose: () => void }) {
  const form = useForm({ resolver: zodResolver(financeReplacementSchema), defaultValues: { userId: null as number | null, guestName: "", reason: "" } })
  const userIdValue = useWatch({ control: form.control, name: "userId" })
  return <Dialog open onOpenChange={open => { if (!open && !finance.pending) onClose() }}><DialogContent><DialogHeader><DialogTitle>Trocar participante</DialogTitle><DialogDescription>A cobrança de {charge.name} será cancelada. Recebimentos anteriores continuam vinculados a ela. A nova pessoa começa sem pagamentos.</DialogDescription></DialogHeader>
    <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(async values => {
      try { await finance.mutate(JSON.stringify({ type: "replace", charge: charge.id, values }), values.reason, command => replaceFinanceCharge(peladaId, list.id, charge.id, command, { components: charge.components, adjustment: charge.adjustment, dueDate: charge.dueDate ?? "", reason: values.reason }, { userId: values.userId, guestName: values.guestName || null })); toast.success("Participante substituído"); onClose() }
      catch (e) { applyServerErrors(form, e, "Não foi possível substituir o participante") }
    })}><FieldGroup><Field><FieldLabel htmlFor="finance-replacement">Novo membro</FieldLabel><Select value={String(userIdValue ?? "none")} onValueChange={v => form.setValue("userId", v === "none" ? null : Number(v))}><SelectTrigger id="finance-replacement"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="none">Selecionar ou usar convidado</SelectItem>{members.filter(m => m.id !== charge.userId).map(m => <SelectItem key={m.id} value={String(m.id)}>{m.username}</SelectItem>)}</SelectGroup></SelectContent></Select></Field>
      <FinanceInput control={form.control} name="guestName" label="Ou novo convidado por nome" /><FinanceInput control={form.control} name="reason" label="Motivo" multiline />
    </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button variant="gradient" type="submit" disabled={finance.pending}>Confirmar troca</Button></form>
  </DialogContent></Dialog>
}
