import { useState, type ReactNode } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Search01Icon } from "@hugeicons/core-free-icons"
import type { PeladaMember } from "@/types/pelada"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldGroup, FieldLabel, FieldLegend, FieldSet } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { normalizeSearch } from "@/lib/utils"

interface Props {
  members: PeladaMember[]
  selected: number[]
  onChange: (ids: number[]) => void
  // prefix of the search and checkbox ids, unique per screen
  idPrefix: string
  // extra filters shown between the search and the list (the people picker's type chips), with their predicate
  filters?: ReactNode
  include?: (member: PeladaMember) => boolean
  // short text at the right of a row ("Mensalista")
  describe?: (member: PeladaMember) => string
}

// Searchable member checklist shared by the people picker and the financial managers card
export function FinanceMemberChecklist({ members, selected, onChange, idPrefix, filters, include, describe }: Props) {
  const [search, setSearch] = useState("")
  const query = normalizeSearch(search)
  const shown = members.filter((member) => normalizeSearch(member.username).includes(query) && (!include || include(member)))
  const allSelected = shown.length > 0 && shown.every((member) => selected.includes(member.id))
  const count = `${selected.length} ${selected.length === 1 ? "selecionado" : "selecionados"}`
  const toggleVisible = () =>
    onChange(
      allSelected
        ? selected.filter((id) => !shown.some((member) => member.id === id))
        : [...new Set([...selected, ...shown.map((member) => member.id)])]
    )
  return (
    <div className="flex flex-col gap-3">
      <Field>
        <FieldLabel htmlFor={`${idPrefix}-search`} className="sr-only">
          Buscar jogador
        </FieldLabel>
        <div className="relative">
          <HugeiconsIcon icon={Search01Icon} aria-hidden className="pointer-events-none absolute left-3 top-3 size-4 text-muted-foreground" />
          <Input
            id={`${idPrefix}-search`}
            type="search"
            placeholder="Buscar jogador"
            className="pl-9"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
      </Field>
      {filters}
      <FieldSet className="gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <FieldLegend variant="label" className="mb-0">
            {count}
            {shown.length < members.length && <span className="font-normal text-muted-foreground"> · mostrando {shown.length} de {members.length}</span>}
          </FieldLegend>
          <Button type="button" size="sm" variant="ghost" disabled={shown.length === 0} onClick={toggleVisible}>
            {allSelected ? "Desmarcar visíveis" : "Selecionar visíveis"}
          </Button>
        </div>
        <FieldGroup className="max-h-80 gap-0 overflow-y-auto rounded-lg border">
          {shown.map((member) => (
            <Field key={member.id} orientation="horizontal" className="items-center gap-3 border-b px-3 py-2.5 last:border-b-0 hover:bg-row-hover">
              <Checkbox
                id={`${idPrefix}-${member.id}`}
                checked={selected.includes(member.id)}
                onCheckedChange={(checked) => onChange(checked ? [...selected, member.id] : selected.filter((id) => id !== member.id))}
              />
              <PlayerAvatar userId={member.id} username={member.username} image={member.image} className="size-8 text-xs" />
              <FieldLabel htmlFor={`${idPrefix}-${member.id}`} className="min-w-0 flex-1 cursor-pointer break-words font-normal">
                {member.username}
              </FieldLabel>
              {describe && <span className="shrink-0 text-xs text-muted-foreground">{describe(member)}</span>}
            </Field>
          ))}
          {shown.length === 0 && (
            <p className="p-4 text-sm text-muted-foreground">
              {query ? `Nenhum jogador encontrado para "${search.trim()}"` : "Nenhum jogador neste filtro."}
            </p>
          )}
        </FieldGroup>
      </FieldSet>
    </div>
  )
}
