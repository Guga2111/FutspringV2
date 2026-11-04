import { useState } from "react"
import { format } from "date-fns"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { useFinanceHistory } from "@/components/finance/hooks/useFinanceHistory"
import { financeMovementLabel, financeMethodLabel, financeAuditGroupLabel, financeAuditGroupOperations } from "@/types/finance"
import type { FinanceAudit, FinanceAuditGroup, FinanceMovement } from "@/types/finance"
import type { PeladaMember } from "@/types/pelada"
import { FINANCE_HISTORY_PAGE_SIZE, reverseFinanceMovement, type FinanceHistoryFilter } from "@/api/finance"
import { ListPagination } from "@/components/ListPagination"
import { DateRangeFilter } from "@/components/DateRangeFilter"
import type { FinanceReasonAction } from "@/components/finance/FinanceReasonForm"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { FinanceFormError } from "@/components/finance/FinanceFields"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { formatFinanceMoney } from "@/utils/finance"
import { auditTitle, auditChanges, auditSubject } from "@/utils/financeAudit"
import { formatInZone, formatShortDate, type DateRange } from "@/utils/dates"

type AuditGroupFilter = "ALL" | FinanceAuditGroup
const MAX_CHANGES = 4

interface FinanceHistoryProps {
  peladaId: number
  kind: "cash" | "audit"
  finance: FinanceController
  // the pelada's zone, for the audit's date and time
  timeZone: string
  members: PeladaMember[]
  onReason: (action: FinanceReasonAction) => void
}

const isoDay = (day: Date) => format(day, "yyyy-MM-dd")

// The cash statement (kind cash) or the audit (kind audit, the Histórico tab), filtered by period and, for the audit, by type
export function FinanceHistory({ peladaId, kind, finance, timeZone, members, onReason }: FinanceHistoryProps) {
  const [range, setRange] = useState<DateRange | undefined>(undefined)
  const [group, setGroup] = useState<AuditGroupFilter>("ALL")
  const filter: FinanceHistoryFilter = {
    from: range?.from ? isoDay(range.from) : undefined,
    to: range?.from ? isoDay(range.to ?? range.from) : undefined,
    operations: kind === "audit" && group !== "ALL" ? financeAuditGroupOperations[group] : undefined,
  }
  const filtered = Boolean(range?.from) || (kind === "audit" && group !== "ALL")
  const history = useFinanceHistory(peladaId, kind, finance.overview?.revision ?? 0, filter)
  const clear = () => { setRange(undefined); setGroup("ALL") }
  // a reversal names the entry it cancels when that entry is on the same page
  const reversedOf = (originalId: number) => {
    const items: (FinanceMovement | FinanceAudit)[] = history.data?.items ?? []
    const original = items.find(entry => entry.id === originalId)
    return original && "kind" in original ? `Anula: ${original.description}` : "Anula um lançamento anterior"
  }
  const total = history.data?.total ?? 0
  const emptyText = filtered
    ? kind === "cash" ? "Nenhum lançamento neste período" : "Nenhuma alteração neste período"
    : kind === "cash" ? "Nenhum lançamento no caixa ainda." : "Nenhuma alteração registrada ainda."
  return <section className="flex min-w-0 flex-col gap-4" aria-label={kind === "cash" ? "Extrato" : "Histórico de alterações"}>
    {kind === "cash" ? (
      <div className="flex flex-col gap-1">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 className="text-lg font-semibold">Extrato do caixa</h3>
          <DateRangeFilter value={range} onChange={setRange} />
        </div>
        {range?.from && history.data && <p className="text-sm text-muted-foreground">{total} {total === 1 ? "lançamento" : "lançamentos"} no período</p>}
      </div>
    ) : (
      <div className="flex flex-wrap items-center justify-between gap-3">
        <ToggleGroup type="single" variant="chip" size="chip" value={group} onValueChange={value => { if (value) setGroup(value as AuditGroupFilter) }} className="flex-wrap justify-start" aria-label="Tipo de alteração">
          <ToggleGroupItem value="ALL">Todos</ToggleGroupItem>
          {(Object.keys(financeAuditGroupLabel) as FinanceAuditGroup[]).map(key => <ToggleGroupItem key={key} value={key}>{financeAuditGroupLabel[key]}</ToggleGroupItem>)}
        </ToggleGroup>
        <DateRangeFilter value={range} onChange={setRange} />
      </div>
    )}
    {history.error ? <><FinanceFormError message={history.error} /><Button className="self-start" variant="outline" onClick={history.retry}>Tentar de novo</Button></>
      : !history.data ? <Skeleton className="h-40 w-full" />
      : history.data.items.length === 0 ? (
        <div className="flex flex-col items-start gap-3 py-8">
          <p className="text-muted-foreground">{emptyText}</p>
          {filtered && <Button variant="outline" onClick={clear}>Limpar filtros</Button>}
        </div>
      )
      : <ul className="flex flex-col divide-y divide-border">{history.data.items.map(item => <li key={item.id} className="flex min-w-0 flex-col gap-3 py-4">
        {"kind" in item ? <>
          <div className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-3"><div className="min-w-0"><p className="break-words font-medium">{item.description}</p><p className="text-sm text-muted-foreground">{financeMovementLabel[item.kind]} · {formatShortDate(item.effectiveDate)} · {financeMethodLabel[item.method]}{item.category ? ` · ${item.category}` : ""}</p>{item.dailyLabel && <p className="text-sm text-muted-foreground">Sessão {item.dailyLabel}</p>}</div><p className="shrink-0 text-right font-semibold tabular-nums">{formatFinanceMoney(item.amount)}</p></div>
          <div className="flex flex-wrap items-center gap-3">{item.reversed && <Badge variant="secondary">Anulado</Badge>}{item.originalId !== null && <span className="text-sm text-muted-foreground">{reversedOf(item.originalId)}</span>}
            {finance.overview?.permissions.manage && !item.reversed && item.kind !== "REVERSAL" && <Button size="sm" variant="outline" disabled={finance.pending} onClick={() => onReason({ title: "Anular lançamento", description: "Use para corrigir um registro equivocado. Para dinheiro devolvido, use Devolver no participante.", signature: `reverse:${item.id}`, run: command => reverseFinanceMovement(peladaId, item.id, command) })}>Anular lançamento</Button>}
          </div>
        </> : <AuditItem entry={item} members={members} timeZone={timeZone} />}
      </li>)}</ul>}
    {history.data && <ListPagination page={history.page} total={total} size={FINANCE_HISTORY_PAGE_SIZE} noun={kind === "cash" ? ["lançamento", "lançamentos"] : ["registro", "registros"]} onPage={history.setPage} />}
  </section>
}

// "Leal · Recebimento registrado", what it touched, when (pelada's zone), the reason and what changed; the raw JSON stays one tap away
function AuditItem({ entry, members, timeZone }: { entry: FinanceAudit; members: PeladaMember[]; timeZone: string }) {
  const subject = auditSubject(entry)
  const changes = auditChanges(entry, members)
  return <>
    <div className="flex min-w-0 flex-col gap-1">
      <p className="break-words"><span className="font-semibold">{entry.actorName}</span> · {auditTitle(entry)}</p>
      {subject && <p className="break-words text-sm">{subject}</p>}
      <p className="text-sm text-muted-foreground">{formatInZone(entry.createdAt, timeZone)}</p>
    </div>
    {entry.reason && <p className="break-words rounded-md bg-muted px-3 py-2 text-sm"><span className="font-medium">Motivo:</span> {entry.reason}</p>}
    {changes.length > 0 && <ul className="flex flex-col gap-1 text-sm">
      {changes.slice(0, MAX_CHANGES).map((change, i) => <li key={`${change.label}-${i}`} className="break-words">
        <span className={change.before || change.after ? "text-muted-foreground" : undefined}>{change.label}</span>
        {change.before !== undefined ? <>: <span className="tabular-nums">{change.before} → {change.after}</span></> : change.after !== undefined ? <>: <span className="tabular-nums">{change.after}</span></> : null}
      </li>)}
      {changes.length > MAX_CHANGES && <li className="text-muted-foreground">e mais {changes.length - MAX_CHANGES}</li>}
    </ul>}
    {(entry.beforeData || entry.afterData) && <Collapsible><CollapsibleTrigger asChild><Button variant="ghost" size="sm" className="-ml-3">Ver dados técnicos</Button></CollapsibleTrigger><CollapsibleContent><div className="flex min-w-0 flex-col gap-3 rounded-md bg-muted p-3 text-sm"><p className="break-all">Antes: {entry.beforeData ?? "—"}</p><p className="break-all">Depois: {entry.afterData ?? "—"}</p></div></CollapsibleContent></Collapsible>}
  </>
}
