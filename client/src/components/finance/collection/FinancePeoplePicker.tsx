import { useState } from "react"
import type { PeladaMember } from "@/types/pelada"
import type { DailyListItem } from "@/types/daily"
import { useFinanceCandidates } from "@/components/finance/hooks/useFinanceCandidates"
import { Button } from "@/components/ui/button"
import { Field, FieldGroup, FieldLabel, FieldDescription } from "@/components/ui/field"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { FinanceFormError } from "@/components/finance/FinanceFields"
import { formatWeekdayDayMonth } from "@/utils/dates"
import { FinanceMemberChecklist } from "@/components/finance/FinanceMemberChecklist"

interface Props {
  peladaId: number
  members: PeladaMember[]
  dailies: DailyListItem[]
  selected: number[]
  onSelect: (ids: number[]) => void
  guests: string[]
  onGuests: (names: string[]) => void
  excluded?: (number | null)[]
}
export function FinancePeoplePicker({ peladaId, members, dailies, selected, onSelect, guests, onGuests, excluded = [] }: Props) {
  const [type, setType] = useState("all")
  const [dailyId, setDailyId] = useState<number | null>(null)
  const candidates = useFinanceCandidates(peladaId, dailyId)
  const available = members.filter(m => !excluded.includes(m.id))
  const eligible = candidates.rows.filter(c => available.some(m => m.id === c.userId))
  return <FieldGroup className="gap-5">
    <FinanceMemberChecklist idPrefix="finance-person" members={available} selected={selected} onChange={onSelect}
      include={m => type === "all" || (type === "monthly") === m.isMensalista}
      describe={m => m.isMensalista ? "Mensalista" : "Diarista"}
      filters={<ToggleGroup type="single" value={type} onValueChange={value => { if (value) setType(value) }} variant="chip" size="chip" className="flex-wrap justify-start" aria-label="Tipo de jogador"><ToggleGroupItem value="all">Todos</ToggleGroupItem><ToggleGroupItem value="monthly">Mensalistas</ToggleGroupItem><ToggleGroupItem value="daily">Diaristas</ToggleGroupItem></ToggleGroup>} />
    {dailies.length > 0 && <Field><FieldLabel htmlFor="finance-attendance-source">Usar participantes de uma sessão</FieldLabel>
      <Select value={String(dailyId ?? "none")} onValueChange={value => setDailyId(value === "none" ? null : Number(value))}>
        <SelectTrigger id="finance-attendance-source"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="none">Selecionar manualmente</SelectItem>{dailies.map(d => <SelectItem key={d.id} value={String(d.id)}>{formatWeekdayDayMonth(d.dailyDate)}</SelectItem>)}</SelectGroup></SelectContent>
      </Select>
      {dailyId !== null && <><Button type="button" variant="outline" disabled={candidates.loading || Boolean(candidates.error) || eligible.length === 0} onClick={() => onSelect([...new Set([...selected, ...eligible.map(c => c.userId)])])}>{candidates.loading ? "Buscando confirmados…" : `Adicionar ${eligible.length} confirmados`}</Button><FieldDescription>Revise quem realmente participou antes de cobrar.</FieldDescription></>}
      <FinanceFormError message={candidates.error ?? undefined} />
    </Field>}
    <Field><FieldLabel htmlFor="finance-guest-names">Convidados (opcional)</FieldLabel><Textarea id="finance-guest-names" placeholder="Um nome por linha" value={guests.join("\n")} onChange={e => onGuests(e.target.value ? e.target.value.split("\n") : [])} aria-describedby="finance-guest-help" /><FieldDescription id="finance-guest-help">Cada nome cria um convidado, sem vincular uma conta.</FieldDescription></Field>
  </FieldGroup>
}
