import { useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowRight01Icon, PlusSignIcon, RefreshIcon } from "@hugeicons/core-free-icons"
import { toast } from "sonner"
import * as api from "@/api/finance"
import type { PeladaMember } from "@/types/pelada"
import type { DailyListItem } from "@/types/daily"
import type { FinanceCharge, FinanceNotice, FinanceTemplate, FinanceList, FinanceMovementKind, FinanceListScope } from "@/types/finance"
import { financeListScopeLabel, financeListStatusLabel, financeListStatusStyle } from "@/types/finance"
import { ListPagination } from "@/components/ListPagination"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { cn } from "@/lib/utils"
import type { FinancePaymentValues } from "@/schemas/finance"
import { useFinance } from "@/components/finance/hooks/useFinance"
import { FinanceListDetail } from "@/components/finance/FinanceListDetail"
import { FinanceCreateForm } from "@/components/finance/FinanceCreateForm"
import { FinanceListForm } from "@/components/finance/FinanceListForm"
import { FinancePaymentForm } from "@/components/finance/FinancePaymentForm"
import { FinanceParticipantsForm } from "@/components/finance/FinanceParticipantsForm"
import { FinanceChargeForm } from "@/components/finance/FinanceChargeForm"
import { FinanceBulkChargeForm } from "@/components/finance/FinanceBulkChargeForm"
import { FinanceChargeDetails } from "@/components/finance/FinanceChargeDetails"
import { FinanceReplacementForm } from "@/components/finance/FinanceReplacementForm"
import { FinanceCashForm } from "@/components/finance/FinanceCashForm"
import { FinanceHistory } from "@/components/finance/FinanceHistory"
import { FinanceSettings } from "@/components/finance/FinanceSettings"
import { FinanceReasonForm, type FinanceReasonAction } from "@/components/finance/FinanceReasonForm"
import { FinanceFormError } from "@/components/finance/FinanceFields"
import { FinanceTour, FinanceTourButton } from "@/components/finance/FinanceTour"
import { formatFinanceMoney } from "@/utils/finance"
import { getErrorMessage } from "@/lib/errors"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs"
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { formatShortDate } from "@/utils/dates"

type EditorAction = { kind: "create" } | { kind: "list"; list?: FinanceList; template?: FinanceTemplate } | { kind: "participants" } | { kind: "details"; charge: FinanceCharge } | { kind: "charge"; charge: FinanceCharge } | { kind: "bulkAdjust"; charges: FinanceCharge[]; onDone: () => void } | { kind: "replace"; charge: FinanceCharge } | { kind: "cash"; cashKind: Extract<FinanceMovementKind, "EXPENSE" | "INCOME" | "OPENING"> } | { kind: "payment"; charge: FinanceCharge; action: FinancePaymentValues["action"]; notice?: FinanceNotice }
type Editor = EditorAction & { revision: number }

// timeZone: the pelada's, for the dates and times of payments and notices
export function FinanceTab({ peladaId, userId, members, dailies, timeZone }: { peladaId: number; userId: number | null; members: PeladaMember[]; dailies: DailyListItem[]; timeZone: string }) {
  const finance = useFinance(peladaId)
  const [tab, setTab] = useState("collections")
  const [editor, setEditor] = useState<Editor | null>(null)
  const [reason, setReason] = useState<(FinanceReasonAction & { revision: number }) | null>(null)
  const overview = finance.overview
  const detail = finance.detail
  const open = (action: EditorAction) => setEditor({ ...action, revision: editor?.revision ?? overview?.revision ?? 0 })
  const close = () => setEditor(null)
  const askReason = (action: FinanceReasonAction) => { close(); setReason({ ...action, revision: editor?.revision ?? overview?.revision ?? 0 }) }
  const formFinance = { ...finance, mutate: (signature: string, message: string, action: Parameters<typeof finance.mutate>[2]) => finance.mutate(signature, message, action, editor?.revision ?? reason?.revision) }
  const payment = (charge: FinanceCharge, action: FinancePaymentValues["action"], notice?: FinanceNotice) => open({ kind: "payment", charge, action, notice })
  async function simple(action: api.FinanceListAction) {
    if (!detail) return
    try { await finance.mutate(`${action}:${detail.collection.id}`, "", command => api.financeListAction(peladaId, detail.collection.id, action, command)); toast.success("Cobrança atualizada") }
    catch (error) { toast.error(getErrorMessage(error, "Não foi possível atualizar a cobrança")) }
  }
  const manage = Boolean(overview?.permissions.manage)
  // the tour points at the collections list, so it brings it on screen first
  const prepareTour = () => { setEditor(null); setReason(null); setTab("collections"); finance.select(null) }
  const collectionRow = (list: FinanceList) => (
    <li key={list.id} className="border-b last:border-b-0">
      <Button variant="ghost" className="h-auto w-full justify-between gap-4 rounded-none px-4 py-4 text-left" onClick={() => finance.select(list.id)}>
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-2">
            <span className="whitespace-normal font-medium">{list.name}</span>
            <Badge variant="status" className={financeListStatusStyle[list.status]}>{financeListStatusLabel[list.status]}</Badge>
          </span>
          <span className="mt-2 block whitespace-normal text-sm text-muted-foreground">
            {manage
              ? `${formatFinanceMoney(list.totals.received)} recebidos${list.totals.pendingNotices > 0 ? ` · ${list.totals.pendingNotices} para conferir` : ""}`
              : `${formatFinanceMoney(list.totals.received)} pago${list.dueDate ? ` · vence ${formatShortDate(list.dueDate)}` : ""}`}
          </span>
        </span>
        <span className="shrink-0 text-right">
          <span className={cn("block font-semibold tabular-nums", !manage && list.totals.outstanding !== "0.00" && "text-highlight-text")}>
            {!manage && list.totals.outstanding === "0.00" ? "Pago" : formatFinanceMoney(list.totals.outstanding)}
          </span>
          <span className="mt-1 block text-xs text-muted-foreground">
            {manage ? "a receber" : list.totals.outstanding === "0.00" ? "tudo certo" : "a pagar"}
          </span>
        </span>
        <HugeiconsIcon icon={ArrowRight01Icon} />
      </Button>
    </li>
  )
  const scopeEmpty: Record<FinanceListScope, { title: string; text: string }> = manage
    ? {
        ACTIVE: { title: "Nenhuma cobrança em aberto", text: "Defina um valor, escolha as pessoas e publique a cobrança." },
        ARCHIVED: { title: "Nenhuma cobrança encerrada", text: "Cobranças encerradas ou canceladas aparecem aqui." },
      }
    : {
        ACTIVE: { title: "Tudo certo por aqui", text: "Quando houver uma cobrança para você, ela aparecerá aqui." },
        ARCHIVED: { title: "Nada encerrado ainda", text: "Suas cobranças encerradas aparecem aqui." },
      }
  return <div className="flex min-w-0 flex-col gap-5">
    {!overview ? finance.error ? <><FinanceFormError message={finance.error} /><Button variant="outline" onClick={finance.refresh}>Tentar de novo</Button></> : <><Skeleton className="h-10 w-64" /><Skeleton className="h-48 w-full" /></> : <>
      <FinanceTour manage={manage} viewCash={overview.permissions.viewCash} canAutoStart={tab === "collections" && finance.selected === null && !editor && !reason}>
      <Tabs value={tab} onValueChange={setTab} className="min-w-0">
        <div className="mb-3 flex items-center justify-between gap-3">
          {/* min-w-0 + horizontal scroll: at 375 px the tabs and the two icon buttons don't fit, the row scrolls instead of the page */}
          <TabsList variant="underline" className="min-w-0 overflow-x-auto scrollbar-none">
            <TabsTrigger variant="underline" value="collections" onClick={() => finance.select(null)}>
              {manage ? "Cobranças" : "Minhas cobranças"}
            </TabsTrigger>
            {overview.permissions.viewCash && <TabsTrigger variant="underline" value="cash" data-tour="cash-tab">Caixa</TabsTrigger>}
            {manage && <TabsTrigger variant="underline" value="settings" data-tour="settings-tab">Configurações</TabsTrigger>}
            {manage && <TabsTrigger variant="underline" value="history">Histórico</TabsTrigger>}
          </TabsList>
          <div className="flex shrink-0 items-center">
            <FinanceTourButton onPrepare={prepareTour} />
            <Button variant="ghost" size="icon" disabled={finance.pending} onClick={finance.refresh} aria-label="Atualizar financeiro"><HugeiconsIcon icon={RefreshIcon} /></Button>
          </div>
        </div>
        {finance.error && <FinanceFormError message={finance.error} />}
        <TabsContent value="collections" className="mt-0 flex flex-col gap-5">
          {finance.selected === null ? <>
            <header className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-xl font-semibold">{manage ? "Cobranças da pelada" : "Minhas cobranças"}</h2>
                <p className="mt-1 text-sm text-muted-foreground">{manage ? "Abra uma cobrança para receber ou conferir pagamentos." : "Veja o que falta pagar, pague com Pix e informe o pagamento."}</p>
              </div>
              {manage && <Button variant="brand" data-tour="new-collection" onClick={() => open({ kind: "create" })} disabled={finance.pending}><HugeiconsIcon icon={PlusSignIcon} className="size-[15px]" strokeWidth={2.2} />Nova cobrança</Button>}
            </header>
            <ToggleGroup type="single" variant="chip" size="chip" data-tour="scope" value={finance.scope} onValueChange={value => { if (value) finance.setScope(value as FinanceListScope) }} className="justify-start" aria-label="Situação das cobranças">
              {(Object.keys(financeListScopeLabel) as FinanceListScope[]).map(scope => <ToggleGroupItem key={scope} value={scope}>{financeListScopeLabel[scope]}</ToggleGroupItem>)}
            </ToggleGroup>
            {overview.lists.length === 0 ? (
              <div data-tour="collections" className="flex flex-col items-start gap-3 rounded-lg border p-6">
                <h3 className="font-medium">{scopeEmpty[finance.scope].title}</h3>
                <p className="text-sm text-muted-foreground">{scopeEmpty[finance.scope].text}</p>
                {manage && finance.scope === "ACTIVE" && <Button variant="outline" onClick={() => open({ kind: "create" })}>Criar cobrança</Button>}
              </div>
            ) : <ul data-tour="collections" className="rounded-lg border">{overview.lists.map(collectionRow)}</ul>}
            <ListPagination page={finance.page} total={overview.total} size={api.FINANCE_LISTS_PAGE_SIZE} noun={["cobrança", "cobranças"]} disabled={finance.pending} onPage={finance.setPage} />
          </> : finance.detailError ? <><FinanceFormError message={finance.detailError} /><Button variant="outline" onClick={finance.refresh}>Tentar de novo</Button><Button variant="ghost" onClick={() => finance.select(null)}>Voltar às cobranças</Button></> : !detail ? <Skeleton className="h-64 w-full" /> : <FinanceListDetail key={detail.collection.id} peladaId={peladaId} userId={userId} finance={finance} members={members} onBulkAdjust={(charges, onDone) => open({ kind: "bulkAdjust", charges, onDone })} onEdit={() => open({ kind: "list", list: detail.collection })} onParticipants={() => open({ kind: "participants" })} onDetails={charge => open({ kind: "details", charge })} onPayment={payment} onReason={askReason} onSimple={action => { void simple(action) }} />}
        </TabsContent>
        {overview.permissions.viewCash && <TabsContent value="cash" className="mt-0 flex flex-col gap-6">
          <header><h2 className="text-xl font-semibold">Caixa da pelada</h2><p className="mt-1 text-sm text-muted-foreground">Dinheiro que já entrou e saiu.</p></header>
          <div className="flex flex-wrap items-end justify-between gap-4 rounded-lg bg-muted p-5">
            <div><p className="text-sm text-muted-foreground">Saldo disponível registrado</p><p className="mt-1 font-display text-score font-bold tabular-nums">{formatFinanceMoney(overview.balance ?? "0.00")}</p></div>
            {manage && <div className="flex flex-wrap gap-2"><Button variant="brand" onClick={() => open({ kind: "cash", cashKind: "EXPENSE" })}><HugeiconsIcon icon={PlusSignIcon} className="size-[15px]" strokeWidth={2.2} />Despesa</Button><Button variant="outline" onClick={() => open({ kind: "cash", cashKind: "INCOME" })}>Outra entrada</Button><DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost">Mais</Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuGroup><DropdownMenuItem onClick={() => open({ kind: "cash", cashKind: "OPENING" })}>Registrar saldo inicial</DropdownMenuItem></DropdownMenuGroup></DropdownMenuContent></DropdownMenu></div>}
          </div>
          {tab === "cash" && <FinanceHistory peladaId={peladaId} kind="cash" finance={finance} timeZone={timeZone} members={members} onReason={askReason} />}
        </TabsContent>}
        {manage && <TabsContent value="settings" className="mt-0 flex flex-col gap-5">
          <header><h2 className="text-xl font-semibold">Configurações do financeiro</h2><p className="mt-1 text-sm text-muted-foreground">Pix, visibilidade, responsáveis e modelos de cobrança.</p></header>
          {tab === "settings" && <FinanceSettings key={overview.revision} peladaId={peladaId} finance={finance} members={members} onTemplate={template => open({ kind: "list", template })} />}
        </TabsContent>}
        {manage && <TabsContent value="history" className="mt-0 flex flex-col gap-5">
          <header><h2 className="text-xl font-semibold">Histórico de alterações</h2><p className="mt-1 text-sm text-muted-foreground">Quem criou, recebeu, corrigiu ou cancelou cada coisa no financeiro, com o motivo.</p></header>
          {tab === "history" && <FinanceHistory peladaId={peladaId} kind="audit" finance={finance} timeZone={timeZone} members={members} onReason={askReason} />}
        </TabsContent>}
      </Tabs>
      </FinanceTour>
    </>}
    {editor?.kind === "create" && <FinanceCreateForm peladaId={peladaId} finance={formFinance} members={members} dailies={dailies} onClose={close} />}
    {editor?.kind === "list" && <FinanceListForm peladaId={peladaId} finance={formFinance} list={editor.list} template={editor.template} asTemplate={!editor.list} dailies={dailies} onClose={close} />}
    {editor?.kind === "participants" && detail && <FinanceParticipantsForm peladaId={peladaId} finance={formFinance} detail={detail} members={members} dailies={dailies} onClose={close} />}
    {editor?.kind === "payment" && detail && <FinancePaymentForm peladaId={peladaId} finance={formFinance} list={detail.collection} charge={editor.charge} action={editor.action} notice={editor.notice} onClose={close} />}
    {editor?.kind === "details" && detail && <FinanceChargeDetails peladaId={peladaId} userId={userId} timeZone={timeZone} finance={formFinance} list={detail.collection} charge={editor.charge} onClose={close} onPayment={payment} onAdjust={() => open({ kind: "charge", charge: editor.charge })} onReplace={() => open({ kind: "replace", charge: editor.charge })} onReason={askReason} />}
    {editor?.kind === "charge" && detail && <FinanceChargeForm peladaId={peladaId} finance={formFinance} list={detail.collection} charge={editor.charge} onClose={close} />}
    {editor?.kind === "bulkAdjust" && detail && <FinanceBulkChargeForm peladaId={peladaId} finance={formFinance} list={detail.collection} charges={editor.charges} onAdjusted={editor.onDone} onClose={close} />}
    {editor?.kind === "replace" && detail && <FinanceReplacementForm peladaId={peladaId} finance={formFinance} list={detail.collection} charge={editor.charge} members={members} excluded={detail.charges.filter(c => !c.canceled).map(c => c.userId)} onClose={close} />}
    {editor?.kind === "cash" && <FinanceCashForm peladaId={peladaId} finance={formFinance} kind={editor.cashKind} dailies={dailies} onClose={close} />}
    {reason && <FinanceReasonForm finance={formFinance} action={reason} onClose={() => setReason(null)} />}
  </div>
}
