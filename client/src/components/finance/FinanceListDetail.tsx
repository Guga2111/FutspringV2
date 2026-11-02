import { useMemo, useState } from "react"
import { MoreHorizontal, Search, Plus } from "lucide-react"
import * as api from "@/api/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import type { FinanceCharge, FinanceNotice } from "@/types/finance"
import { financeListStatusLabel } from "@/types/finance"
import type { FinanceReasonAction } from "@/components/finance/FinanceReasonForm"
import type { FinancePaymentValues } from "@/schemas/finance"
import { filterFinanceCharges, financeQueueCounts, financeQueueLabels, pendingFinanceNotice, formatFinanceMoney, type FinanceQueueFilter } from "@/utils/finance"
import { FinanceImage } from "@/components/finance/FinanceImage"
import { FinanceQrForm } from "@/components/finance/FinanceSettings"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Field, FieldLabel } from "@/components/ui/field"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"

export function FinanceListDetail({ peladaId, userId, finance, onEdit, onParticipants, onDetails, onPayment, onReason, onSimple }: {
  peladaId: number; userId: number | null; finance: FinanceController; onEdit: () => void; onParticipants: () => void
  onDetails: (charge: FinanceCharge) => void
  onPayment: (charge: FinanceCharge, action: FinancePaymentValues["action"], notice?: FinanceNotice) => void
  onReason: (action: FinanceReasonAction) => void; onSimple: (action: api.FinanceListAction) => void
}) {
  const [filter, setFilter] = useState<FinanceQueueFilter>("pending")
  const [search, setSearch] = useState("")
  const detail = finance.detail
  const charges = detail?.charges
  const rows = useMemo(() => filterFinanceCharges(charges ?? [], filter, search), [charges, filter, search])
  if (!detail) return null
  const list = detail.collection
  const manage = Boolean(finance.overview?.permissions.manage)
  const editable = manage && list.status !== "CANCELED"
  const counts = financeQueueCounts(detail.charges)
  return <section className="flex min-w-0 flex-col gap-3 md:gap-5" aria-label={list.name}>
    <header className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h2 className="break-words text-xl font-semibold">{list.name}</h2><Badge variant="secondary">{financeListStatusLabel[list.status]}</Badge></div><p className="mt-1 text-sm text-muted-foreground">{list.dueDate ? `Vence ${list.dueDate.split("-").reverse().join("/")}` : "Sem vencimento"}{list.dailyLabel ? ` · Sessão ${list.dailyLabel}` : ""}</p></div>      {manage && <DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" size="icon" aria-label="Opções da cobrança" disabled={finance.pending}><MoreHorizontal /></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuGroup>
        {editable && <DropdownMenuItem onClick={onParticipants}>Adicionar pessoas</DropdownMenuItem>}
        {editable && <DropdownMenuItem onClick={onEdit}>Editar cobrança e Pix</DropdownMenuItem>}
        {list.status === "DRAFT" && <DropdownMenuItem onClick={() => onSimple("publish")}>Publicar cobrança</DropdownMenuItem>}
        {list.status === "OPEN" && <DropdownMenuItem onClick={() => onSimple("close")}>Encerrar cobrança</DropdownMenuItem>}
        {list.status === "CLOSED" && <DropdownMenuItem onClick={() => onReason({ title: "Reabrir cobrança", description: "A cobrança voltará a aceitar avisos e recebimentos.", signature: `reopen:${list.id}`, run: command => api.financeListAction(peladaId, list.id, "reopen", command) })}>Reabrir cobrança</DropdownMenuItem>}
        <DropdownMenuItem onClick={() => onSimple("duplicate")}>Duplicar como rascunho</DropdownMenuItem>
        {editable && <DropdownMenuItem onClick={() => onReason({ title: "Cancelar cobrança", description: "Só é possível cancelar sem recebimentos válidos nem avisos pendentes. As movimentações de caixa são preservadas.", signature: `cancel:${list.id}`, run: command => api.financeListAction(peladaId, list.id, "cancel", command) })}>Cancelar cobrança</DropdownMenuItem>}
      </DropdownMenuGroup></DropdownMenuContent></DropdownMenu>}
{editable && <Button className="hidden md:inline-flex" variant="outline" size="sm" onClick={onParticipants} disabled={finance.pending}><Plus data-icon="inline-start" />Pessoas</Button>}</header>
    <dl className="grid grid-cols-2 gap-4 rounded-lg bg-muted p-3 md:p-4"><div><dt className="text-sm text-muted-foreground">{manage || finance.overview?.permissions.viewCash ? "Recebido" : "Você já pagou"}</dt><dd className="mt-1 font-semibold tabular-nums">{formatFinanceMoney(list.totals.received)}</dd></div><div><dt className="text-sm text-muted-foreground">{manage || finance.overview?.permissions.viewCash ? "Falta receber" : "Falta pagar"}</dt><dd className="mt-1 font-semibold tabular-nums">{formatFinanceMoney(list.totals.outstanding)}</dd></div></dl>
    {list.status === "DRAFT" && manage && <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"><p className="text-sm">Rascunho: os jogadores ainda não veem esta cobrança.</p><Button variant="brand" size="sm" disabled={finance.pending || counts.all === 0} onClick={() => onSimple("publish")}>Publicar cobrança</Button></div>}
    {list.status === "CLOSED" && <p className="text-sm text-muted-foreground">Cobrança encerrada. As pendências continuam registradas.</p>}
    <div className="flex flex-wrap items-center justify-between gap-3"><ToggleGroup type="single" value={filter} onValueChange={value => { if (value) setFilter(value as FinanceQueueFilter) }} variant="chip" size="chip" className="flex-wrap justify-start" aria-label="Situação dos pagamentos">{(Object.keys(financeQueueLabels) as FinanceQueueFilter[]).map(value => <ToggleGroupItem className="px-2 max-md:min-h-11" value={value} key={value}>{financeQueueLabels[value]} <span className="tabular-nums">{counts[value]}</span></ToggleGroupItem>)}</ToggleGroup>
      <Field className="w-full lg:w-56"><FieldLabel htmlFor="finance-payment-search" className="sr-only">Buscar participante</FieldLabel><div className="relative"><Search aria-hidden className="pointer-events-none absolute left-3 top-3 size-4 text-muted-foreground" /><Input id="finance-payment-search" placeholder="Buscar participante" className="pl-9" value={search} onChange={event => setSearch(event.target.value)} /></div></Field>
    </div>
    {rows.length === 0 ? <div className="flex flex-col items-start gap-3 rounded-lg border p-6"><h3 className="font-medium">{counts.all === 0 ? "Quem vai participar desta cobrança?" : filter === "pending" && !search ? "Nenhum pagamento pendente" : "Nenhum participante neste filtro"}</h3><p className="text-sm text-muted-foreground">{counts.all === 0 ? "Adicione membros da pelada ou convidados." : "Os registros continuam disponíveis nos outros filtros."}</p>{counts.all === 0 && editable ? <Button variant="brand" size="sm" onClick={onParticipants}>Adicionar pessoas</Button> : filter !== "all" && <Button size="sm" variant="outline" onClick={() => setFilter("all")}>Ver todos</Button>}</div> : <ul className="flex flex-col rounded-lg border">{rows.map(charge => {
      const notice = pendingFinanceNotice(charge)
      const canReceive = manage && list.status === "OPEN" && !charge.canceled && !notice && charge.paymentStatus !== "PAID"
      const canReport = !manage && charge.userId === userId && list.status === "OPEN" && !charge.canceled && !notice && charge.paymentStatus !== "PAID"
      return <li key={charge.id} className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-2 border-b p-3 last:border-b-0 lg:grid-cols-[minmax(0,1fr)_140px_180px] md:p-4"><div className="flex min-w-0 items-center gap-3"><PlayerAvatar username={charge.name} image={null} className="size-9" /><div className="min-w-0"><p className="break-words text-sm font-medium">{charge.name}</p><p className="mt-0.5 text-xs text-muted-foreground">{charge.canceled ? "Cancelada" : charge.paymentStatus === "PAID" ? "Pago" : charge.paymentStatus === "PARTIAL" ? "Pagamento parcial" : "Aguardando pagamento"}{notice ? " · Pagamento para conferir" : ""}{charge.overdue ? " · Vencido" : ""}{charge.userId === null ? " · Convidado" : ""}</p></div></div><p className="text-right text-sm font-semibold tabular-nums">{formatFinanceMoney(charge.paymentStatus === "PAID" || charge.canceled ? charge.amountDue : charge.outstanding)}</p>
        <div className="col-span-2 flex justify-end gap-2 lg:col-span-1"><Button className="max-md:min-h-11" variant="ghost" size="sm" onClick={() => onDetails(charge)}>Detalhes</Button>{notice && manage ? <Button className="max-md:min-h-11" size="sm" variant="outline" onClick={() => onDetails(charge)} disabled={finance.pending}>Conferir</Button> : canReceive ? <Button variant="brand" className="max-md:min-h-11" size="sm" onClick={() => onPayment(charge, "receive")} disabled={finance.pending}>Receber</Button> : canReport ? <Button variant="brand" className="max-md:min-h-11" size="sm" onClick={() => onPayment(charge, "report")} disabled={finance.pending}>Informar pagamento</Button> : null}</div>
      </li>
    })}</ul>}
    <Collapsible><CollapsibleTrigger asChild><Button variant="ghost" size="sm">Informações da cobrança e Pix</Button></CollapsibleTrigger><CollapsibleContent className="pt-3"><div className="flex flex-col gap-4 rounded-lg bg-muted p-4">{list.description && <p className="whitespace-pre-wrap break-words text-sm">{list.description}</p>}<p className="text-sm">Valor total: {formatFinanceMoney(list.totals.due)}</p>{list.pixKey && <p className="break-all text-sm">Pix: {list.pixKey}{list.pixRecipient ? ` · ${list.pixRecipient}` : ""}</p>}{list.qrId && <FinanceImage peladaId={peladaId} kind="qr" imageId={list.qrId} />}{editable && <FinanceQrForm peladaId={peladaId} finance={finance} listId={list.id} />}</div></CollapsibleContent></Collapsible>
  </section>
}
