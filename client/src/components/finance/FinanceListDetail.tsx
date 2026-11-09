import { useMemo, useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowLeft02Icon, Cancel01Icon, CheckListIcon, MoreHorizontalIcon, PlusSignIcon, Search01Icon } from "@hugeicons/core-free-icons"
import * as api from "@/api/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import type { FinanceCharge, FinanceNotice } from "@/types/finance"
import type { PeladaMember } from "@/types/pelada"
import { financeChargeStatusLabel, financeChargeStatusStyle, financeListStatusLabel, financeListStatusStyle } from "@/types/finance"
import type { FinanceReasonAction } from "@/components/finance/FinanceReasonForm"
import type { FinancePaymentValues } from "@/schemas/finance"
import { adjustChargeHint, canAdjustCharge, financeChargeStatus, filterFinanceCharges, mensalistaCharges, planBulkFinanceActions, financeQueueCounts, financeQueueLabels, pendingFinanceNotice, formatFinanceMoney, sumFinanceAmounts, type FinanceQueueFilter } from "@/utils/finance"
import { FinancePixCard } from "@/components/finance/FinancePixCard"
import { FinanceQrField } from "@/components/finance/FinanceQrField"
import { FinanceReceiveAllDialog } from "@/components/finance/FinanceReceiveAllDialog"
import { FinanceWhatsAppDialog } from "@/components/finance/FinanceWhatsAppDialog"
import { SelectionBar } from "@/components/SelectionBar"
import { useSelection } from "@/hooks/useSelection"
import { cn } from "@/lib/utils"
import { toast } from "sonner"
import { getErrorMessage } from "@/lib/errors"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldLabel } from "@/components/ui/field"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { formatShortDate } from "@/utils/dates"

export function FinanceListDetail({ peladaId, userId, timeZone, finance, members, onEdit, onParticipants, onDetails, onPayment, onReason, onSimple, onBulkAdjust }: {
  // timeZone: the pelada's, for "Vence" / "Venceu" in the WhatsApp message
  peladaId: number; userId: number | null; timeZone: string; finance: FinanceController; members: PeladaMember[]; onEdit: () => void; onParticipants: () => void
  onDetails: (charge: FinanceCharge) => void
  onPayment: (charge: FinanceCharge, action: FinancePaymentValues["action"], notice?: FinanceNotice) => void
  onReason: (action: FinanceReasonAction) => void; onSimple: (action: api.FinanceListAction) => void
  // "Ajustar valor" of the selected charges; onDone leaves selection mode after a successful save
  onBulkAdjust: (charges: FinanceCharge[], onDone: () => void) => void
}) {
  const [filter, setFilter] = useState<FinanceQueueFilter>("pending")
  const [search, setSearch] = useState("")
  const [receivingAll, setReceivingAll] = useState(false)
  const [sharing, setSharing] = useState(false)
  const detail = finance.detail
  const charges = detail?.charges
  // individual mode: a member only receives their own charges, so filters and search have nothing to sort
  const queue = Boolean(finance.overview?.permissions.viewCash)
  const allCharges = useMemo(() => charges ?? [], [charges])
  const rows = useMemo(() => filterFinanceCharges(allCharges, queue ? filter : "all", search), [allCharges, queue, filter, search])
  // Selection mode ("Receber" / "Ajustar valor"): every charge that is not canceled and has no payment to review is
  // selectable, paid ones included; the selection survives search, queue chips and refreshes, and each action of the
  // bar counts only the charges it changes (planBulkFinanceActions)
  const selection = useSelection(allCharges, rows, canAdjustCharge)
  if (!detail) return null
  const list = detail.collection
  const manage = Boolean(finance.overview?.permissions.manage)
  const editable = manage && list.status !== "CANCELED"
  const counts = financeQueueCounts(detail.charges)
  const owed = sumFinanceAmounts(detail.charges.filter(c => c.userId === userId && !c.canceled).map(c => c.outstanding))
  const payable = !manage && list.status === "OPEN" && owed !== "0.00"
  const open = list.status === "OPEN"
  const selectable = manage && queue && (open || list.status === "DRAFT") && detail.charges.some(canAdjustCharge)
  const selecting = selectable && selection.active
  const plan = planBulkFinanceActions(selection.selected)
  const visibleSelectable = rows.filter(canAdjustCharge).length
  const toggleVisibleLabel = selection.allVisibleSelected ? "Desmarcar todos" : `Selecionar todos (${visibleSelectable})`
  const mensalistas = mensalistaCharges(detail.charges, members)
  const selectMensalistas = () => selection.select(mensalistas.map(c => c.id))
  const mensalistasLabel = `Selecionar mensalistas (${mensalistas.length})`
  const adjustSelected = () => onBulkAdjust(plan.adjust.charges, selection.exit)
  const adjustLabel = `Ajustar valor (${plan.adjust.charges.length})`
  const receiveLabel = `Receber ${plan.receive.charges.length === 1 ? "1 selecionado" : `${plan.receive.charges.length} selecionados`}`
  async function uploadQr(file: File) {
    const run = (command: Parameters<FinanceReasonAction["run"]>[0]) => api.uploadFinanceQr(peladaId, list.id, command, file)
    // after publication a change to the Pix instructions needs a reason, like any other edit
    if (list.publishedAt) { onReason({ title: "Trocar QR Code da cobrança", description: "Quem já pagou não é afetado. Informe por que o QR Code mudou.", signature: `qr:${list.id}:${file.name}:${file.size}`, run }); return }
    try { await finance.mutate(`qr:${list.id}:${file.name}:${file.size}`, "", run); toast.success("QR Code salvo") }
    catch (error) { toast.error(getErrorMessage(error, "Não foi possível enviar o QR Code")) }
  }
  return <section className="flex min-w-0 flex-col gap-3 md:gap-5" aria-label={list.name}>
    <Button className="self-start" variant="ghost" size="sm" onClick={() => finance.select(null)}><HugeiconsIcon icon={ArrowLeft02Icon} data-icon="inline-start" className="size-4" />Cobranças</Button>
    <header className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h2 className="break-words text-xl font-semibold">{list.name}</h2><Badge variant="status" className={financeListStatusStyle[list.status]}>{financeListStatusLabel[list.status]}</Badge></div><p className="mt-1 text-sm text-muted-foreground">{list.dueDate ? `Vence ${formatShortDate(list.dueDate)}` : "Sem vencimento"}{list.dailyLabel ? ` · Sessão ${list.dailyLabel}` : ""}</p></div>      {manage && <DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" size="icon" aria-label="Opções da cobrança" disabled={finance.pending}><HugeiconsIcon icon={MoreHorizontalIcon} /></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuGroup>
        {editable && <DropdownMenuItem disabled={!finance.writable} onClick={onParticipants}>Adicionar pessoas</DropdownMenuItem>}
        {editable && <DropdownMenuItem disabled={!finance.writable} onClick={onEdit}>Editar cobrança e Pix</DropdownMenuItem>}
        {list.status === "DRAFT" && <DropdownMenuItem disabled={!finance.writable} onClick={() => onSimple("publish")}>Publicar cobrança</DropdownMenuItem>}
        {list.status === "OPEN" && <DropdownMenuItem onClick={() => setSharing(true)}>Compartilhar no WhatsApp</DropdownMenuItem>}
        {list.status === "OPEN" && <DropdownMenuItem disabled={!finance.writable} onClick={() => onSimple("close")}>Encerrar cobrança</DropdownMenuItem>}
        {list.status === "CLOSED" && <DropdownMenuItem disabled={!finance.writable} onClick={() => onReason({ title: "Reabrir cobrança", description: "A cobrança voltará a aceitar avisos e recebimentos.", signature: `reopen:${list.id}`, run: command => api.financeListAction(peladaId, list.id, "reopen", command) })}>Reabrir cobrança</DropdownMenuItem>}
        <DropdownMenuItem disabled={!finance.writable} onClick={() => onSimple("duplicate")}>Duplicar como rascunho</DropdownMenuItem>
        {editable && <DropdownMenuItem disabled={!finance.writable} onClick={() => onReason({ title: "Cancelar a cobrança inteira", description: "Só é possível cancelar sem recebimentos válidos nem avisos pendentes. As movimentações de caixa são preservadas.", signature: `cancel:${list.id}`, run: command => api.financeListAction(peladaId, list.id, "cancel", command) })}>Cancelar cobrança inteira</DropdownMenuItem>}
      </DropdownMenuGroup></DropdownMenuContent></DropdownMenu>}
{editable && <Button className="hidden md:inline-flex" variant="outline" size="sm" onClick={onParticipants} disabled={finance.locked}><HugeiconsIcon icon={PlusSignIcon} data-icon="inline-start" className="size-4" />Pessoas</Button>}</header>
    <dl className="grid grid-cols-2 gap-4 rounded-lg bg-muted p-3 md:p-4"><div><dt className="text-sm text-muted-foreground">{manage || finance.overview?.permissions.viewCash ? "Recebido" : "Você já pagou"}</dt><dd className="mt-1 font-display text-heading font-bold tabular-nums">{formatFinanceMoney(list.totals.received)}</dd></div><div><dt className="text-sm text-muted-foreground">{manage || finance.overview?.permissions.viewCash ? "Falta receber" : "Falta pagar"}</dt><dd className="mt-1 font-display text-heading font-bold tabular-nums">{formatFinanceMoney(list.totals.outstanding)}</dd></div></dl>
    {payable && <FinancePixCard peladaId={peladaId} pixKey={list.pixKey} pixRecipient={list.pixRecipient} qrId={list.qrId} amount={owed} />}
    {list.status === "DRAFT" && manage && <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"><p className="text-sm">Rascunho: os jogadores ainda não veem esta cobrança.</p><Button variant="brand" size="sm" disabled={finance.locked || counts.all === 0} onClick={() => onSimple("publish")}>Publicar cobrança</Button></div>}
    {list.status === "CLOSED" && <p className="text-sm text-muted-foreground">Cobrança encerrada. As pendências continuam registradas.</p>}
    {queue && <div className="flex flex-wrap items-center justify-between gap-3"><ToggleGroup type="single" value={filter} onValueChange={value => { if (value) setFilter(value as FinanceQueueFilter) }} variant="chip" size="chip" className="flex-wrap justify-start" aria-label="Situação dos pagamentos">{(Object.keys(financeQueueLabels) as FinanceQueueFilter[]).map(value => <ToggleGroupItem className="group max-md:min-h-11" value={value} key={value}>{financeQueueLabels[value]}<span className="text-xs tabular-nums text-subtle-foreground group-data-[state=on]:text-primary-foreground/60">{counts[value]}</span></ToggleGroupItem>)}</ToggleGroup>
      <div className="flex w-full flex-wrap items-center gap-2 lg:w-auto lg:flex-nowrap">
        <Field className="min-w-0 flex-1 lg:w-56 lg:flex-none"><FieldLabel htmlFor="finance-payment-search" className="sr-only">Buscar participante</FieldLabel><div className="relative"><HugeiconsIcon icon={Search01Icon} aria-hidden className="pointer-events-none absolute left-3 top-3 size-4 text-muted-foreground" /><Input id="finance-payment-search" placeholder="Buscar participante" className="pl-9" value={search} onChange={event => setSearch(event.target.value)} /></div></Field>
        {selectable && <Button variant="outline" className="max-md:min-h-11 max-sm:w-full" onClick={selecting ? selection.exit : selection.enter} aria-pressed={selecting} disabled={!finance.writable}><HugeiconsIcon icon={selecting ? Cancel01Icon : CheckListIcon} data-icon="inline-start" className="size-4" />{selecting ? "Cancelar" : "Selecionar"}</Button>}
      </div>
    </div>}
    {selecting && <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-body-sm text-subtle-foreground">{selection.selected.length === 0 && <span>Toque nas cobranças para selecionar</span>}{visibleSelectable > 0 && <Button variant="link" onClick={selection.toggleVisible} className="h-auto p-0 text-body-sm">{toggleVisibleLabel}</Button>}{mensalistas.length > 0 && <Button variant="link" onClick={selectMensalistas} className="h-auto p-0 text-body-sm">{mensalistasLabel}</Button>}</p>}
    {sharing && <FinanceWhatsAppDialog peladaId={peladaId} detail={detail} timeZone={timeZone} onClose={() => setSharing(false)} />}
    {receivingAll && <FinanceReceiveAllDialog peladaId={peladaId} finance={finance} list={list} charges={plan.receive.charges} total={plan.receive.total} review={plan.receive.review} skipped={plan.receive.skipped} onReceived={selection.exit} onClose={() => setReceivingAll(false)} />}
    {rows.length === 0 ? <div className="flex flex-col items-start gap-3 rounded-lg border p-6"><h3 className="font-medium">{counts.all === 0 ? "Quem vai participar desta cobrança?" : filter === "pending" && !search ? "Nenhum pagamento pendente" : "Nenhum participante neste filtro"}</h3><p className="text-sm text-muted-foreground">{counts.all === 0 ? "Adicione membros da pelada ou convidados." : "Os registros continuam disponíveis nos outros filtros."}</p>{counts.all === 0 && editable ? <Button variant="brand" size="sm" disabled={!finance.writable} onClick={onParticipants}>Adicionar pessoas</Button> : filter !== "all" && <Button size="sm" variant="outline" onClick={() => setFilter("all")}>Ver todos</Button>}</div> : <ul className="flex flex-col rounded-lg border">{rows.map(charge => {
      const notice = pendingFinanceNotice(charge)
      const status = financeChargeStatus(charge)
      const canReceive = manage && list.status === "OPEN" && !charge.canceled && !notice && charge.paymentStatus !== "PAID"
      const canReport = !manage && charge.userId === userId && list.status === "OPEN" && !charge.canceled && !notice && charge.paymentStatus !== "PAID"
      const hint = selecting ? adjustChargeHint(charge) : null
      const checked = selection.selectedIds.has(charge.id)
      return <li key={charge.id} onClick={selecting && !hint ? () => selection.toggle(charge.id) : undefined} className={cn("grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-2 border-b p-3 last:border-b-0 lg:grid-cols-[minmax(0,1fr)_minmax(112px,auto)_auto] md:p-4", selecting && !hint && "cursor-pointer select-none hover:bg-row-hover", checked && "bg-muted hover:bg-muted")}><div className="flex min-w-0 items-center gap-3">{selecting && <Checkbox checked={checked} disabled={hint !== null} onCheckedChange={() => selection.toggle(charge.id)} onClick={event => event.stopPropagation()} aria-label={`Selecionar ${charge.name}`} className="shrink-0" />}<PlayerAvatar userId={charge.userId} username={charge.name} image={null} className="size-9" /><div className="min-w-0"><p className="break-words text-sm font-medium">{charge.name}</p><p className="mt-1 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-muted-foreground"><Badge variant="status" className={financeChargeStatusStyle[status]}>{financeChargeStatusLabel[status]}</Badge>{[notice && "Pagamento para conferir", charge.overdue && "Vencida", charge.userId === null && "Convidado"].filter(Boolean).join(" · ")}</p></div></div><p className="text-right text-sm font-semibold tabular-nums">{formatFinanceMoney(charge.paymentStatus === "PAID" || charge.canceled ? charge.amountDue : charge.outstanding)}</p>
        {selecting ? <p className="col-span-2 text-right text-xs text-subtle-foreground lg:col-span-1">{hint}</p> : <div className="col-span-2 flex justify-end gap-2 lg:col-span-1"><Button className="max-md:min-h-11" variant="ghost" size="sm" onClick={() => onDetails(charge)}>Detalhes</Button>{notice && manage ? <Button className="max-md:min-h-11" size="sm" variant="outline" onClick={() => onDetails(charge)} disabled={finance.pending}>Conferir</Button> : canReceive ? <Button variant="brand" className="max-md:min-h-11" size="sm" onClick={() => onPayment(charge, "receive")} disabled={finance.locked}>Receber</Button> : canReport ? <Button variant="brand" className="max-md:min-h-11" size="sm" onClick={() => onPayment(charge, "report")} disabled={finance.locked}>Informar pagamento</Button> : null}</div>}
      </li>
    })}</ul>}
    {selecting && selection.selected.length > 0 && <SelectionBar count={selection.selected.length} summary={open && plan.receive.charges.length > 0 ? <><span className="max-md:hidden"> · </span><span className="whitespace-nowrap max-md:block">{formatFinanceMoney(plan.receive.total)}<span className="max-md:hidden"> a receber</span></span></> : undefined} note={open ? plan.receive.hint : null} outsideFilter={selection.outsideFilter} pending={finance.pending} onExit={selection.exit}>
      <div className="ml-auto hidden items-center gap-2 md:flex">
        <Button variant="ghost" className="hidden h-9 gap-1.5 px-3.5 lg:inline-flex" disabled={finance.pending || visibleSelectable === 0} onClick={selection.toggleVisible}><HugeiconsIcon icon={CheckListIcon} className="size-4" />{toggleVisibleLabel}</Button>
        {mensalistas.length > 0 && <Button variant="ghost" className="h-9 px-3.5" disabled={finance.pending} onClick={selectMensalistas}>{mensalistasLabel}</Button>}
        <Button variant="outline" className="h-9 px-4" disabled={finance.pending || plan.adjust.hint !== null} onClick={adjustSelected}>{adjustLabel}</Button>
      </div>
      {/* below md: the secondary actions in one menu, Receber stays a button */}
      <DropdownMenu><DropdownMenuTrigger asChild><Button variant="outline" size="icon" aria-label="Mais ações" className="ml-auto size-9 shrink-0 md:hidden" disabled={finance.pending}><HugeiconsIcon icon={MoreHorizontalIcon} className="size-4" /></Button></DropdownMenuTrigger>
        <DropdownMenuContent align="center" side="top" className="w-60"><DropdownMenuGroup>
          {visibleSelectable > 0 && <DropdownMenuItem onClick={selection.toggleVisible}>{toggleVisibleLabel}</DropdownMenuItem>}
          {mensalistas.length > 0 && <DropdownMenuItem onClick={selectMensalistas}>{mensalistasLabel}</DropdownMenuItem>}
          <DropdownMenuItem disabled={plan.adjust.hint !== null} onClick={adjustSelected}>{adjustLabel}</DropdownMenuItem>
        </DropdownMenuGroup></DropdownMenuContent>
      </DropdownMenu>
      {open && <Button variant="brand" className="h-9 shrink-0 px-4" disabled={finance.pending || plan.receive.hint !== null} onClick={() => setReceivingAll(true)} aria-label={receiveLabel}>Receber<span className="max-md:hidden"> ({plan.receive.charges.length})</span></Button>}
    </SelectionBar>}
    <Collapsible><CollapsibleTrigger asChild><Button variant="ghost" size="sm">{payable ? "Informações da cobrança" : "Informações da cobrança e Pix"}</Button></CollapsibleTrigger><CollapsibleContent className="pt-3"><div className="flex flex-col gap-4 rounded-lg bg-muted p-4">{list.description && <p className="whitespace-pre-wrap break-words text-sm">{list.description}</p>}<p className="text-sm">Valor total: {formatFinanceMoney(list.totals.due)}</p>{!payable && <FinancePixCard peladaId={peladaId} pixKey={list.pixKey} pixRecipient={list.pixRecipient} qrId={list.qrId} />}{!list.pixKey && list.qrId === null && <p className="text-sm text-muted-foreground">Esta cobrança não tem Pix cadastrado.</p>}{editable && <FinanceQrField peladaId={peladaId} qrId={list.qrId} busy={finance.pending} onUpload={uploadQr} preview={payable || list.qrId === null} />}</div></CollapsibleContent></Collapsible>
  </section>
}
