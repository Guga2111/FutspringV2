import { useState } from "react"
import { toast } from "sonner"
import * as api from "@/api/finance"
import type { PeladaMember } from "@/types/pelada"
import type { DailyListItem } from "@/types/daily"
import type { FinanceCharge, FinanceNotice, FinanceTemplate, FinanceList } from "@/types/finance"
import { financeListStatusLabel } from "@/types/finance"
import type { FinancePaymentValues } from "@/schemas/finance"
import { useFinance } from "@/components/finance/hooks/useFinance"
import { FinanceListDetail } from "@/components/finance/FinanceListDetail"
import { FinanceListForm } from "@/components/finance/FinanceListForm"
import { FinancePaymentForm } from "@/components/finance/FinancePaymentForm"
import { FinanceParticipantsForm } from "@/components/finance/FinanceParticipantsForm"
import { FinanceChargeForm } from "@/components/finance/FinanceChargeForm"
import { FinanceReplacementForm } from "@/components/finance/FinanceReplacementForm"
import { FinanceCashForm } from "@/components/finance/FinanceCashForm"
import { FinanceHistory } from "@/components/finance/FinanceHistory"
import { FinanceSettings } from "@/components/finance/FinanceSettings"
import { FinanceReasonForm, type FinanceReasonAction } from "@/components/finance/FinanceReasonForm"
import { FinanceFormError } from "@/components/finance/FinanceFields"
import { formatFinanceMoney } from "@/utils/finance"
import { getErrorMessage } from "@/lib/errors"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs"

type Editor = { kind: "list"; list?: FinanceList; template?: FinanceTemplate } | { kind: "participants" } | { kind: "charge"; charge: FinanceCharge } | { kind: "replace"; charge: FinanceCharge } | { kind: "cash" } | { kind: "payment"; charge: FinanceCharge; action: FinancePaymentValues["action"]; notice?: FinanceNotice }
export function FinanceTab({ peladaId, userId, members, dailies }: { peladaId: number; userId: number | null; members: PeladaMember[]; dailies: DailyListItem[] }) {
  const finance = useFinance(peladaId)
  const [tab, setTab] = useState("lists")
  const [editor, setEditor] = useState<Editor | null>(null)
  const [reason, setReason] = useState<FinanceReasonAction | null>(null)
  const overview = finance.overview
  const detail = finance.detail
  const close = () => setEditor(null)
  async function simple(action: api.FinanceListAction) {
    if (!detail) return
    try { await finance.mutate(`${action}:${detail.collection.id}`, "", command => api.financeListAction(peladaId, detail.collection.id, action, command)); toast.success("Lista atualizada") }
    catch (e) { toast.error(getErrorMessage(e, "Não foi possível atualizar a lista")) }
  }
  return <div className="flex min-w-0 flex-col gap-5">
    <header className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-xl font-semibold">Financeiro</h2><p className="text-sm text-muted-foreground">{overview?.permissions.manage ? "Cobranças, conferência e caixa da pelada" : "Acompanhe as cobranças e informe seus pagamentos"}</p></div><Button variant="outline" disabled={finance.pending} onClick={finance.refresh}>Atualizar</Button></header>
    {finance.error && <><FinanceFormError message={finance.error} /><Button variant="outline" onClick={finance.refresh}>Tentar de novo</Button></>}
    {!overview ? !finance.error && <><Skeleton className="h-24 w-full" /><Skeleton className="h-48 w-full" /></> : <>
      {overview.balance !== null && <div className="flex flex-wrap items-end justify-between gap-4 rounded-md bg-muted p-5"><div><p className="text-sm text-muted-foreground">Saldo registrado</p><p className="text-2xl font-semibold tabular-nums">{formatFinanceMoney(overview.balance)}</p></div><p className="max-w-sm text-sm text-muted-foreground">Avisos pendentes e valores a receber não entram neste saldo.</p></div>}
      <Tabs value={tab} onValueChange={setTab} className="min-w-0"><TabsList className="mb-5 flex h-auto flex-wrap justify-start"><TabsTrigger value="lists">Listas</TabsTrigger>{overview.permissions.viewCash && <TabsTrigger value="cash">Extrato</TabsTrigger>}{overview.permissions.manage && <><TabsTrigger value="audit">Auditoria</TabsTrigger><TabsTrigger value="settings">Configurações</TabsTrigger></>}</TabsList>
        <TabsContent value="lists" className="mt-0 flex flex-col gap-4">
          {finance.selected === null ? <>
            <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="text-lg font-semibold">Listas de cobrança</h3>{overview.permissions.manage && <Button onClick={() => setEditor({ kind: "list" })} disabled={finance.pending}>Nova lista</Button>}</div>
            {overview.lists.length === 0 ? <div className="flex flex-col gap-2 py-10"><p className="font-medium">{overview.permissions.manage ? "Prepare sua primeira arrecadação" : "Nenhuma cobrança disponível"}</p><p className="text-sm text-muted-foreground">{overview.permissions.manage ? "Crie uma lista, revise os participantes e publique as cobranças." : "Suas cobranças aparecem aqui quando o gestor publica uma lista."}</p></div> : <ul className="flex flex-col divide-y divide-border">{overview.lists.map(list => <li key={list.id} className="flex flex-wrap items-center justify-between gap-3 py-4"><div className="min-w-0"><p className="break-words font-medium">{list.name}</p><div className="mt-1 flex flex-wrap items-center gap-2"><Badge variant="secondary">{financeListStatusLabel[list.status]}</Badge><span className="text-sm text-muted-foreground">A receber: {formatFinanceMoney(list.totals.outstanding)}</span>{list.totals.pendingNotices > 0 && <span className="text-sm">{list.totals.pendingNotices} para conferir</span>}</div></div><Button variant="outline" onClick={() => finance.select(list.id)}>Abrir lista</Button></li>)}</ul>}
            {(finance.page > 0 || overview.hasMore) && <div className="flex justify-between gap-3"><Button variant="outline" disabled={finance.page === 0} onClick={() => finance.setPage(v => v - 1)}>Anterior</Button><Button variant="outline" disabled={!overview.hasMore} onClick={() => finance.setPage(v => v + 1)}>Próxima</Button></div>}
            {overview.permissions.manage && <section className="flex flex-col gap-3 pt-5"><h3 className="text-lg font-semibold">Modelos reutilizáveis</h3><div className="flex flex-wrap gap-2">{overview.templates.map(t => <Button key={t.id ?? t.classification} variant="outline" onClick={() => setEditor({ kind: "list", template: t })}>{t.name}{t.archived ? " (arquivado)" : ""}</Button>)}</div></section>}
          </> : finance.detailError ? <><FinanceFormError message={finance.detailError} /><Button variant="outline" onClick={finance.refresh}>Tentar de novo</Button><Button variant="ghost" onClick={() => finance.select(null)}>Voltar às listas</Button></> : !detail ? <Skeleton className="h-64 w-full" /> : <FinanceListDetail peladaId={peladaId} userId={userId} finance={finance} onEdit={() => setEditor({ kind: "list", list: detail.collection })} onParticipants={() => setEditor({ kind: "participants" })} onCharge={charge => setEditor({ kind: "charge", charge })} onReplace={charge => setEditor({ kind: "replace", charge })} onPayment={(charge, action, notice) => setEditor({ kind: "payment", charge, action, notice })} onReason={setReason} onSimple={action => { void simple(action) }} />}
        </TabsContent>
        {overview.permissions.viewCash && <TabsContent value="cash" className="mt-0 flex flex-col gap-4">{overview.permissions.manage && <Button className="self-start" onClick={() => setEditor({ kind: "cash" })}>Registrar movimentação</Button>}{tab === "cash" && <FinanceHistory peladaId={peladaId} kind="cash" finance={finance} onReason={setReason} />}</TabsContent>}
        {overview.permissions.manage && <><TabsContent value="audit" className="mt-0">{tab === "audit" && <FinanceHistory peladaId={peladaId} kind="audit" finance={finance} onReason={setReason} />}</TabsContent><TabsContent value="settings" className="mt-0">{tab === "settings" && <FinanceSettings key={overview.revision} peladaId={peladaId} finance={finance} members={members} />}</TabsContent></>}
      </Tabs>
    </>}
    {editor?.kind === "list" && <FinanceListForm peladaId={peladaId} finance={finance} list={editor.list} template={editor.template} dailies={dailies} onClose={close} />}
    {editor?.kind === "participants" && detail && <FinanceParticipantsForm peladaId={peladaId} finance={finance} detail={detail} members={members} dailies={dailies} onClose={close} />}
    {editor?.kind === "payment" && detail && <FinancePaymentForm peladaId={peladaId} finance={finance} list={detail.collection} charge={editor.charge} action={editor.action} notice={editor.notice} onClose={close} />}
    {editor?.kind === "charge" && detail && <FinanceChargeForm peladaId={peladaId} finance={finance} list={detail.collection} charge={editor.charge} onClose={close} />}
    {editor?.kind === "replace" && detail && <FinanceReplacementForm peladaId={peladaId} finance={finance} list={detail.collection} charge={editor.charge} members={members} onClose={close} />}
    {editor?.kind === "cash" && <FinanceCashForm peladaId={peladaId} finance={finance} dailies={dailies} onClose={close} />}
    {reason && <FinanceReasonForm finance={finance} action={reason} onClose={() => setReason(null)} />}
  </div>
}
