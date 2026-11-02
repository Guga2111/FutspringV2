import { useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowLeft02Icon, ArrowRight01Icon, PlusSignIcon, RefreshIcon, Settings02Icon } from "@hugeicons/core-free-icons"
import { toast } from "sonner"
import * as api from "@/api/finance"
import type { PeladaMember } from "@/types/pelada"
import type { DailyListItem } from "@/types/daily"
import type { FinanceCharge, FinanceNotice, FinanceTemplate, FinanceList, FinanceMovementKind } from "@/types/finance"
import { financeListStatusLabel } from "@/types/finance"
import type { FinancePaymentValues } from "@/schemas/finance"
import { useFinance } from "@/components/finance/hooks/useFinance"
import { FinanceListDetail } from "@/components/finance/FinanceListDetail"
import { FinanceCreateForm } from "@/components/finance/FinanceCreateForm"
import { FinanceListForm } from "@/components/finance/FinanceListForm"
import { FinancePaymentForm } from "@/components/finance/FinancePaymentForm"
import { FinanceParticipantsForm } from "@/components/finance/FinanceParticipantsForm"
import { FinanceChargeForm } from "@/components/finance/FinanceChargeForm"
import { FinanceChargeDetails } from "@/components/finance/FinanceChargeDetails"
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
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"

type EditorAction = { kind: "create" } | { kind: "list"; list?: FinanceList; template?: FinanceTemplate } | { kind: "participants" } | { kind: "details"; charge: FinanceCharge } | { kind: "charge"; charge: FinanceCharge } | { kind: "replace"; charge: FinanceCharge } | { kind: "cash"; cashKind: Extract<FinanceMovementKind, "EXPENSE" | "INCOME" | "OPENING"> } | { kind: "payment"; charge: FinanceCharge; action: FinancePaymentValues["action"]; notice?: FinanceNotice }
type Editor = EditorAction & { revision: number }

export function FinanceTab({ peladaId, userId, members, dailies }: { peladaId: number; userId: number | null; members: PeladaMember[]; dailies: DailyListItem[] }) {
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
  const collectionRow = (list: FinanceList) => <li key={list.id}><Button variant="ghost" className="h-auto w-full justify-between gap-4 rounded-lg px-4 py-4 text-left" onClick={() => finance.select(list.id)}><span className="min-w-0 flex-1"><span className="flex flex-wrap items-center gap-2"><span className="whitespace-normal font-medium">{list.name}</span><Badge variant="secondary">{financeListStatusLabel[list.status]}</Badge></span><span className="mt-2 block whitespace-normal text-sm text-muted-foreground">{formatFinanceMoney(list.totals.received)} recebidos{list.totals.pendingNotices > 0 ? ` · ${list.totals.pendingNotices} para conferir` : ""}</span></span><span className="shrink-0 text-right"><span className="block font-semibold tabular-nums">{formatFinanceMoney(list.totals.outstanding)}</span><span className="mt-1 block text-xs text-muted-foreground">a receber</span></span><HugeiconsIcon icon={ArrowRight01Icon} /></Button></li>
  return <div className="flex min-w-0 flex-col gap-5">
    {!overview ? finance.error ? <><FinanceFormError message={finance.error} /><Button variant="outline" onClick={finance.refresh}>Tentar de novo</Button></> : <><Skeleton className="h-10 w-64" /><Skeleton className="h-48 w-full" /></> : <>
      <Tabs value={tab} onValueChange={setTab} className="min-w-0">
        <div className="mb-3 flex items-center justify-between gap-3"><TabsList className="h-auto"><TabsTrigger value="collections" onClick={() => finance.select(null)}>{finance.selected !== null && <HugeiconsIcon icon={ArrowLeft02Icon} className="mr-2 size-4" />}{overview.permissions.manage ? "Cobranças" : "Minhas cobranças"}</TabsTrigger>{overview.permissions.viewCash && <TabsTrigger value="cash">Caixa</TabsTrigger>}</TabsList><div className="flex items-center gap-1"><Button variant="ghost" size="icon" disabled={finance.pending} onClick={finance.refresh} aria-label="Atualizar financeiro"><HugeiconsIcon icon={RefreshIcon} /></Button>{overview.permissions.manage && <DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" size="icon" aria-label="Configurações do financeiro"><HugeiconsIcon icon={Settings02Icon} /></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuGroup><DropdownMenuItem onClick={() => setTab("settings")}>Pix, modelos e responsáveis</DropdownMenuItem><DropdownMenuItem onClick={() => setTab("audit")}>Histórico de alterações</DropdownMenuItem></DropdownMenuGroup></DropdownMenuContent></DropdownMenu>}</div></div>
        {finance.error && <FinanceFormError message={finance.error} />}
        <TabsContent value="collections" className="mt-0 flex flex-col gap-5">
          {finance.selected === null ? <>
            <header className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-xl font-semibold">{overview.permissions.manage ? "Cobranças da pelada" : "Minhas cobranças"}</h2><p className="mt-1 text-sm text-muted-foreground">{overview.permissions.manage ? "Abra uma cobrança para receber ou conferir pagamentos." : "Consulte o que falta pagar e informe seu pagamento."}</p></div>{overview.permissions.manage && <Button onClick={() => open({ kind: "create" })} disabled={finance.pending}><HugeiconsIcon icon={PlusSignIcon} data-icon="inline-start" />Nova cobrança</Button>}</header>
            {overview.lists.length === 0 ? <div className="flex flex-col items-start gap-3 rounded-lg border p-6"><h3 className="font-medium">{overview.permissions.manage ? "Comece pela próxima diária" : "Tudo certo por aqui"}</h3><p className="text-sm text-muted-foreground">{overview.permissions.manage ? "Defina um valor, escolha as pessoas e publique a cobrança." : "Quando houver uma cobrança para você, ela aparecerá aqui."}</p>{overview.permissions.manage && <Button variant="outline" onClick={() => open({ kind: "create" })}>Criar primeira cobrança</Button>}</div> : <>
              {overview.lists.some(list => list.status === "OPEN" || list.status === "DRAFT") && <ul className="rounded-lg border">{overview.lists.filter(list => list.status === "OPEN" || list.status === "DRAFT").map(collectionRow)}</ul>}
              {overview.lists.some(list => list.status === "CLOSED" || list.status === "CANCELED") && <Collapsible><CollapsibleTrigger asChild><Button variant="ghost" size="sm">Cobranças encerradas e canceladas</Button></CollapsibleTrigger><CollapsibleContent className="pt-3"><ul className="rounded-lg border">{overview.lists.filter(list => list.status === "CLOSED" || list.status === "CANCELED").map(collectionRow)}</ul></CollapsibleContent></Collapsible>}
            </>}
            {(finance.page > 0 || overview.hasMore) && <div className="flex justify-between gap-3"><Button variant="outline" disabled={finance.page === 0} onClick={() => finance.setPage(value => value - 1)}>Anterior</Button><Button variant="outline" disabled={!overview.hasMore} onClick={() => finance.setPage(value => value + 1)}>Mais cobranças</Button></div>}
          </> : finance.detailError ? <><FinanceFormError message={finance.detailError} /><Button variant="outline" onClick={finance.refresh}>Tentar de novo</Button><Button variant="ghost" onClick={() => finance.select(null)}>Voltar às cobranças</Button></> : !detail ? <Skeleton className="h-64 w-full" /> : <FinanceListDetail key={detail.collection.id} peladaId={peladaId} userId={userId} finance={finance} onEdit={() => open({ kind: "list", list: detail.collection })} onParticipants={() => open({ kind: "participants" })} onDetails={charge => open({ kind: "details", charge })} onPayment={payment} onReason={askReason} onSimple={action => { void simple(action) }} />}
        </TabsContent>
        {overview.permissions.viewCash && <TabsContent value="cash" className="mt-0 flex flex-col gap-6"><header><h2 className="text-xl font-semibold">Caixa da pelada</h2><p className="mt-1 text-sm text-muted-foreground">Dinheiro que já entrou e saiu.</p></header><div className="flex flex-wrap items-end justify-between gap-4 rounded-lg bg-muted p-5"><div><p className="text-sm text-muted-foreground">Saldo disponível registrado</p><p className="mt-1 text-2xl font-semibold tabular-nums">{formatFinanceMoney(overview.balance ?? "0.00")}</p></div>{overview.permissions.manage && <div className="flex flex-wrap gap-2"><Button onClick={() => open({ kind: "cash", cashKind: "EXPENSE" })}><HugeiconsIcon icon={PlusSignIcon} data-icon="inline-start" />Despesa</Button><Button variant="outline" onClick={() => open({ kind: "cash", cashKind: "INCOME" })}>Outra entrada</Button><DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost">Mais</Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuGroup><DropdownMenuItem onClick={() => open({ kind: "cash", cashKind: "OPENING" })}>Registrar saldo inicial</DropdownMenuItem></DropdownMenuGroup></DropdownMenuContent></DropdownMenu></div>}</div>{tab === "cash" && <FinanceHistory peladaId={peladaId} kind="cash" finance={finance} onReason={askReason} />}</TabsContent>}
        {overview.permissions.manage && <><TabsContent value="audit" className="mt-0 flex flex-col gap-5"><Button className="self-start" variant="ghost" onClick={() => setTab("collections")}><HugeiconsIcon icon={ArrowLeft02Icon} data-icon="inline-start" />Voltar às cobranças</Button>{tab === "audit" && <FinanceHistory peladaId={peladaId} kind="audit" finance={finance} onReason={askReason} />}</TabsContent><TabsContent value="settings" className="mt-0 flex flex-col gap-5"><Button className="self-start" variant="ghost" onClick={() => setTab("collections")}><HugeiconsIcon icon={ArrowLeft02Icon} data-icon="inline-start" />Voltar às cobranças</Button><h2 className="text-xl font-semibold">Configurações do financeiro</h2>{tab === "settings" && <><FinanceSettings key={overview.revision} peladaId={peladaId} finance={finance} members={members} /><section className="flex flex-col gap-3"><h3 className="text-lg font-semibold">Modelos de cobrança</h3><p className="text-sm text-muted-foreground">Salve os valores que você costuma cobrar.</p><div className="flex flex-wrap gap-2">{overview.templates.map(template => <Button key={template.id ?? template.classification} variant="outline" onClick={() => open({ kind: "list", template })}>{template.name}</Button>)}<Button variant="outline" onClick={() => open({ kind: "list" })}>Novo modelo</Button></div></section></>}</TabsContent></>}
      </Tabs>
    </>}
    {editor?.kind === "create" && <FinanceCreateForm peladaId={peladaId} finance={formFinance} members={members} dailies={dailies} onClose={close} />}
    {editor?.kind === "list" && <FinanceListForm peladaId={peladaId} finance={formFinance} list={editor.list} template={editor.template} asTemplate={!editor.list} dailies={dailies} onClose={close} />}
    {editor?.kind === "participants" && detail && <FinanceParticipantsForm peladaId={peladaId} finance={formFinance} detail={detail} members={members} dailies={dailies} onClose={close} />}
    {editor?.kind === "payment" && detail && <FinancePaymentForm peladaId={peladaId} finance={formFinance} list={detail.collection} charge={editor.charge} action={editor.action} notice={editor.notice} onClose={close} />}
    {editor?.kind === "details" && detail && <FinanceChargeDetails peladaId={peladaId} userId={userId} finance={formFinance} list={detail.collection} charge={editor.charge} onClose={close} onPayment={payment} onAdjust={() => open({ kind: "charge", charge: editor.charge })} onReplace={() => open({ kind: "replace", charge: editor.charge })} onReason={askReason} />}
    {editor?.kind === "charge" && detail && <FinanceChargeForm peladaId={peladaId} finance={formFinance} list={detail.collection} charge={editor.charge} onClose={close} />}
    {editor?.kind === "replace" && detail && <FinanceReplacementForm peladaId={peladaId} finance={formFinance} list={detail.collection} charge={editor.charge} members={members} onClose={close} />}
    {editor?.kind === "cash" && <FinanceCashForm peladaId={peladaId} finance={formFinance} kind={editor.cashKind} dailies={dailies} onClose={close} />}
    {reason && <FinanceReasonForm finance={formFinance} action={reason} onClose={() => setReason(null)} />}
  </div>
}
