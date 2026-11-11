import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { HugeiconsIcon } from "@hugeicons/react"
import { Tick02Icon, UnfoldMoreIcon } from "@hugeicons/core-free-icons"
import { financeReplacementSchema } from "@/schemas/finance"
import { replaceFinanceCharge } from "@/api/finance"
import type { FinanceCharge, FinanceList } from "@/types/finance"
import type { PeladaMember } from "@/types/pelada"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceInput, FinanceFormError } from "@/components/finance/FinanceFields"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Button } from "@/components/ui/button"
import { applyServerErrors } from "@/lib/form-errors"
import { cn, normalizeSearch } from "@/lib/utils"
import { toast } from "sonner"

// cmdk matches on the item value (the member id); the name travels in keywords, compared without accents
const filterMembers = (_value: string, search: string, keywords?: string[]) =>
  normalizeSearch(keywords?.join(" ") ?? "").includes(normalizeSearch(search)) ? 1 : 0

// excluded: userIds of the list's active charges; the server allows one active charge per person
export function FinanceReplacementForm({ peladaId, finance, charge, list, members, excluded, onClose }: { peladaId: number; finance: FinanceController; charge: FinanceCharge; list: FinanceList; members: PeladaMember[]; excluded: (number | null)[]; onClose: () => void }) {
  const form = useForm({ resolver: zodResolver(financeReplacementSchema), defaultValues: { userId: null as number | null, guestName: "", reason: "" } })
  const [open, setOpen] = useState(false)
  const options = members.filter(m => m.id !== charge.userId && !excluded.includes(m.id))
  return <Dialog open onOpenChange={value => { if (!value && !finance.pending) onClose() }}><DialogContent><DialogHeader><DialogTitle>Trocar participante</DialogTitle><DialogDescription>A cobrança de {charge.name} será cancelada. Recebimentos anteriores continuam vinculados a ela. A nova pessoa começa sem pagamentos.</DialogDescription></DialogHeader>
    <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(async values => {
      try { await finance.mutate(JSON.stringify({ type: "replace", charge: charge.id, values }), values.reason, command => replaceFinanceCharge(peladaId, list.id, charge.id, command, { components: charge.components, adjustment: charge.adjustment, dueDate: charge.dueDate ?? "", reason: values.reason }, { userId: values.userId, guestName: values.guestName || null })); toast.success("Participante trocado"); onClose() }
      catch (e) { applyServerErrors(form, e, "Não foi possível substituir o participante") }
    })}><FieldGroup>
      <Controller control={form.control} name="userId" render={({ field, fieldState }) => {
        const chosen = options.find(m => m.id === field.value)
        return <Field data-invalid={fieldState.invalid}><FieldLabel htmlFor="finance-replacement">Novo membro</FieldLabel>
          <Popover open={open} onOpenChange={setOpen}>
            <PopoverTrigger asChild><Button id="finance-replacement" type="button" variant="outline" role="combobox" aria-expanded={open} aria-invalid={fieldState.invalid} className="w-full justify-between rounded-md font-normal">
              {chosen ? <span className="flex min-w-0 items-center gap-2"><PlayerAvatar userId={chosen.id} username={chosen.username} image={chosen.image} className="size-6 text-[10px]" /><span className="truncate">{chosen.username}</span></span> : <span className="truncate text-muted-foreground">Buscar membro ou usar convidado</span>}
              <HugeiconsIcon icon={UnfoldMoreIcon} className="ml-2 size-4 shrink-0 opacity-50" />
            </Button></PopoverTrigger>
            <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
              <Command filter={filterMembers}>
                <CommandInput placeholder="Buscar membro" />
                <CommandList>
                  <CommandEmpty>Nenhum membro encontrado</CommandEmpty>
                  <CommandGroup>
                    {field.value !== null && <CommandItem value="none" keywords={["Usar convidado"]} onSelect={() => { field.onChange(null); setOpen(false) }}>Nenhum membro (usar convidado)</CommandItem>}
                    {options.map(m => <CommandItem key={m.id} value={String(m.id)} keywords={[m.username]} onSelect={() => { field.onChange(m.id); setOpen(false) }}>
                      <HugeiconsIcon icon={Tick02Icon} className={cn("size-4", m.id === field.value ? "opacity-100" : "opacity-0")} />
                      <PlayerAvatar userId={m.id} username={m.username} image={m.image} className="size-6 text-[10px]" />
                      <span className="truncate">{m.username}</span>
                    </CommandItem>)}
                  </CommandGroup>
                </CommandList>
              </Command>
            </PopoverContent>
          </Popover>
          <FieldError errors={[fieldState.error]} />
        </Field>
      }} />
      <FinanceInput control={form.control} name="guestName" label="Ou novo convidado por nome" /><FinanceInput control={form.control} name="reason" label="Motivo" multiline />
    </FieldGroup><FinanceFormError message={form.formState.errors.root?.message} /><Button variant="brand" type="submit" disabled={finance.pending}>Confirmar troca</Button></form>
  </DialogContent></Dialog>
}
