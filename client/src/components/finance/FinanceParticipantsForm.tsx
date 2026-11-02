import { useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { addFinanceParticipants } from "@/api/finance"
import { financePeopleFormSchema } from "@/schemas/finance"
import type { FinanceDetail } from "@/types/finance"
import type { PeladaMember } from "@/types/pelada"
import type { DailyListItem } from "@/types/daily"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceFormError } from "@/components/finance/FinanceFields"
import { FinancePeoplePicker } from "@/components/finance/FinancePeoplePicker"
import { Button } from "@/components/ui/button"
import { FieldGroup, FieldError } from "@/components/ui/field"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { applyServerErrors } from "@/lib/form-errors"

export function FinanceParticipantsForm({ peladaId, finance, detail, members, dailies, onClose }: { peladaId: number; finance: FinanceController; detail: FinanceDetail; members: PeladaMember[]; dailies: DailyListItem[]; onClose: () => void }) {
  const form = useForm({ resolver: zodResolver(financePeopleFormSchema), defaultValues: { userIds: [] as number[], guestNames: [] as string[], reason: "" } })
  const selected = useWatch({ control: form.control, name: "userIds" })
  const guests = useWatch({ control: form.control, name: "guestNames" })
  const busy = finance.pending || form.formState.isSubmitting
  return <Sheet open onOpenChange={open => { if (!open && !busy) onClose() }}><SheetContent className="flex w-full flex-col gap-6 overflow-y-auto sm:max-w-xl">
    <SheetHeader><SheetTitle>Adicionar pessoas</SheetTitle><SheetDescription>{detail.collection.name}. Quem já participa não precisa ser adicionado novamente.</SheetDescription></SheetHeader>
    <form noValidate className="flex flex-1 flex-col gap-6" onSubmit={form.handleSubmit(async values => {
      try { await finance.mutate(JSON.stringify({ type: "participants", listId: detail.collection.id, values }), values.reason, command => addFinanceParticipants(peladaId, detail.collection.id, command, values)); toast.success("Pessoas adicionadas"); onClose() }
      catch (error) { applyServerErrors(form, error, "Não foi possível adicionar as pessoas") }
    })}><FieldGroup><FinancePeoplePicker peladaId={peladaId} members={members} dailies={dailies} selected={selected} onSelect={ids => form.setValue("userIds", ids)} guests={guests} onGuests={names => form.setValue("guestNames", names)} excluded={detail.charges.filter(charge => !charge.canceled).map(charge => charge.userId)} /><FieldError errors={[form.formState.errors.userIds, form.formState.errors.guestNames]} />{detail.collection.publishedAt && <FinanceInput control={form.control} name="reason" label="Motivo de adicionar após a publicação" multiline />}</FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button variant="brand" className="mt-auto" type="submit" disabled={busy}>Adicionar à cobrança</Button></form>
  </SheetContent></Sheet>
}
