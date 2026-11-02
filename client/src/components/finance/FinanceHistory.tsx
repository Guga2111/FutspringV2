import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { useFinanceHistory } from "@/components/finance/hooks/useFinanceHistory"
import { financeMovementLabel, financeMethodLabel, financeAuditLabel } from "@/types/finance"
import { FINANCE_HISTORY_PAGE_SIZE, reverseFinanceMovement } from "@/api/finance"
import { FinancePagination } from "@/components/finance/FinancePagination"
import type { FinanceReasonAction } from "@/components/finance/FinanceReasonForm"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { FinanceFormError } from "@/components/finance/FinanceFields"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { formatFinanceMoney } from "@/utils/finance"

export function FinanceHistory({ peladaId, kind, finance, onReason }: { peladaId: number; kind: "cash" | "audit"; finance: FinanceController; onReason: (action: FinanceReasonAction) => void }) {
  const history = useFinanceHistory(peladaId, kind, finance.overview?.revision ?? 0)
  return <section className="flex flex-col gap-4" aria-label={kind === "cash" ? "Extrato" : "Auditoria"}>
    <h3 className="text-lg font-semibold">{kind === "cash" ? "Extrato do caixa" : "Histórico de alterações"}</h3>
    {history.error ? <><FinanceFormError message={history.error} /><Button variant="outline" onClick={history.retry}>Tentar de novo</Button></> : !history.data ? <Skeleton className="h-40 w-full" /> : history.data.items.length === 0 ? <p className="py-8 text-muted-foreground">{kind === "cash" ? "Nenhum lançamento no caixa ainda." : "Nenhuma alteração registrada ainda."}</p> : <ul className="flex flex-col divide-y divide-border">{history.data.items.map(item => <li key={item.id} className="flex min-w-0 flex-col gap-3 py-4">
      {"kind" in item ? <>
        <div className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-3"><div className="min-w-0"><p className="break-words font-medium">{item.description}</p><p className="text-sm text-muted-foreground">{financeMovementLabel[item.kind]} · {item.effectiveDate.split("-").reverse().join("/")} · {financeMethodLabel[item.method]}{item.category ? ` · ${item.category}` : ""}</p>{item.dailyLabel && <p className="text-sm text-muted-foreground">Diária: {item.dailyLabel}</p>}</div><p className="shrink-0 text-right font-semibold tabular-nums">{formatFinanceMoney(item.amount)}</p></div>
        <div className="flex flex-wrap items-center gap-3">{item.reversed && <Badge variant="secondary">Anulado</Badge>}{item.originalId && <span className="text-sm text-muted-foreground">Corrige lançamento #{item.originalId}</span>}
          {finance.overview?.permissions.manage && !item.reversed && item.kind !== "REVERSAL" && <Button size="sm" variant="outline" disabled={finance.pending} onClick={() => onReason({ title: "Anular lançamento", description: "Use para corrigir um registro equivocado. Para dinheiro devolvido, use Devolver no participante.", signature: `reverse:${item.id}`, run: command => reverseFinanceMovement(peladaId, item.id, command) })}>Anular lançamento</Button>}
        </div>
      </> : <>
        <p className="break-words font-medium">{item.actorName} · {financeAuditLabel[item.operation]}</p><p className="text-sm text-muted-foreground">{new Date(item.createdAt).toLocaleString("pt-BR")}</p>{item.reason && <p className="break-words text-sm">{item.reason}</p>}
        <Collapsible><CollapsibleTrigger asChild><Button variant="ghost" size="sm">Ver dados anteriores e novos</Button></CollapsibleTrigger><CollapsibleContent><div className="flex min-w-0 flex-col gap-3 rounded-md bg-muted p-3 text-sm"><p className="break-all">Antes: {item.beforeData ?? "—"}</p><p className="break-all">Depois: {item.afterData ?? "—"}</p></div></CollapsibleContent></Collapsible>
      </>}
    </li>)}</ul>}
    {history.data && <FinancePagination page={history.page} total={history.data.total} size={FINANCE_HISTORY_PAGE_SIZE} noun={kind === "cash" ? ["lançamento", "lançamentos"] : ["registro", "registros"]} onPage={history.setPage} />}
  </section>
}
