import { useState } from "react"
import type { PeladaMember } from "@/types/pelada"
import type { DailyListItem } from "@/types/daily"
import { useFinanceCandidates } from "@/components/finance/hooks/useFinanceCandidates"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldGroup, FieldLabel, FieldDescription, FieldSet, FieldLegend } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { FinanceFormError } from "@/components/finance/FinanceFields"

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
  const [search, setSearch] = useState("")
  const [type, setType] = useState("all")
  const [dailyId, setDailyId] = useState<number | null>(null)
  const candidates = useFinanceCandidates(peladaId, dailyId)
  const available = members.filter(m => !excluded.includes(m.id))
  const normalize = (value: string) => value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("pt-BR")
  const shown = available.filter(m => normalize(m.username).includes(normalize(search)) && (type === "all" || (type === "monthly") === m.isMensalista))
  const eligible = candidates.rows.filter(c => available.some(m => m.id === c.userId))
  const allSelected = shown.length > 0 && shown.every(m => selected.includes(m.id))
  return <FieldGroup className="gap-5">
    {dailies.length > 0 && <Field><FieldLabel htmlFor="finance-attendance-source">Usar participantes de uma sessão</FieldLabel>
      <Select value={String(dailyId ?? "none")} onValueChange={value => setDailyId(value === "none" ? null : Number(value))}>
        <SelectTrigger id="finance-attendance-source"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="none">Selecionar manualmente</SelectItem>{dailies.map(d => <SelectItem key={d.id} value={String(d.id)}>{d.dailyDate.split("-").reverse().join("/")}</SelectItem>)}</SelectGroup></SelectContent>
      </Select>
      {dailyId !== null && <><Button type="button" variant="outline" disabled={candidates.loading || Boolean(candidates.error) || eligible.length === 0} onClick={() => onSelect([...new Set([...selected, ...eligible.map(c => c.userId)])])}>{candidates.loading ? "Buscando confirmados…" : `Adicionar ${eligible.length} confirmados`}</Button><FieldDescription>Revise quem realmente participou antes de cobrar.</FieldDescription></>}
      <FinanceFormError message={candidates.error ?? undefined} />
    </Field>}
    <Field><FieldLabel htmlFor="finance-find-player">Buscar jogador</FieldLabel><Input id="finance-find-player" placeholder="Nome do jogador" value={search} onChange={e => setSearch(e.target.value)} /></Field>
    <ToggleGroup type="single" value={type} onValueChange={value => { if (value) setType(value) }} variant="chip" size="chip" className="flex-wrap justify-start" aria-label="Tipo de jogador"><ToggleGroupItem value="all">Todos</ToggleGroupItem><ToggleGroupItem value="monthly">Mensalistas</ToggleGroupItem><ToggleGroupItem value="daily">Diaristas</ToggleGroupItem></ToggleGroup>
    <FieldSet className="gap-3"><div className="flex flex-wrap items-center justify-between gap-2"><FieldLegend className="mb-0">{selected.length} selecionados</FieldLegend><Button type="button" size="sm" variant="ghost" disabled={shown.length === 0} onClick={() => onSelect(allSelected ? selected.filter(id => !shown.some(m => m.id === id)) : [...new Set([...selected, ...shown.map(m => m.id)])])}>{allSelected ? "Desmarcar visíveis" : "Selecionar visíveis"}</Button></div>
      <FieldGroup className="max-h-64 gap-0 overflow-y-auto rounded-lg border">
        {shown.map(member => <Field key={member.id} orientation="horizontal" className="gap-3 px-3 py-3 hover:bg-accent"><Checkbox id={`finance-person-${member.id}`} checked={selected.includes(member.id)} onCheckedChange={checked => onSelect(checked ? [...selected, member.id] : selected.filter(id => id !== member.id))} /><FieldLabel htmlFor={`finance-person-${member.id}`} className="flex-1 cursor-pointer">{member.username}</FieldLabel><span className="text-xs text-muted-foreground">{member.isMensalista ? "Mensalista" : "Diarista"}</span></Field>)}
        {shown.length === 0 && <p className="p-4 text-sm text-muted-foreground">Nenhum jogador neste filtro.</p>}
      </FieldGroup>
    </FieldSet>
    <Field><FieldLabel htmlFor="finance-guest-names">Convidados (opcional)</FieldLabel><Textarea id="finance-guest-names" placeholder="Um nome por linha" value={guests.join("\n")} onChange={e => onGuests(e.target.value ? e.target.value.split("\n") : [])} aria-describedby="finance-guest-help" /><FieldDescription id="finance-guest-help">Cada nome cria um convidado, sem vincular uma conta.</FieldDescription></Field>
  </FieldGroup>
}
