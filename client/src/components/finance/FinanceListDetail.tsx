import { FinanceNotices } from "@/components/finance/FinanceNotices"
import * as api from "@/api/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import type { FinanceCharge, FinanceNotice } from "@/types/finance"
import { financeListStatusLabel, financePaymentLabel } from "@/types/finance"
import type { FinanceReasonAction } from "@/components/finance/FinanceReasonForm"
import type { FinancePaymentValues } from "@/schemas/finance"
import { formatFinanceMoney } from "@/utils/finance"
import { FinanceImage } from "@/components/finance/FinanceImage"
import { FinanceQrForm } from "@/components/finance/FinanceSettings"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card"
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"

export function FinanceListDetail({ peladaId, userId, finance, onEdit, onParticipants, onCharge, onReplace, onPayment, onReason, onSimple }: {
  peladaId: number; userId: number | null; finance: FinanceController; onEdit: () => void; onParticipants: () => void
  onCharge: (charge: FinanceCharge) => void; onReplace: (charge: FinanceCharge) => void
  onPayment: (charge: FinanceCharge, action: FinancePaymentValues["action"], notice?: FinanceNotice) => void
  onReason: (action: FinanceReasonAction) => void; onSimple: (action: api.FinanceListAction) => void
}) {
  const detail = finance.detail
  if (!detail) return null
  const list = detail.collection
  const manage = Boolean(finance.overview?.permissions.manage)
  const open = list.status === "OPEN"
  const editable = manage && list.status !== "CANCELED"
  return <section className="flex min-w-0 flex-col gap-5">
    <div className="flex flex-wrap items-center justify-between gap-3"><Button variant="ghost" onClick={() => finance.select(null)}>Voltar às listas</Button>
      {manage && <DropdownMenu><DropdownMenuTrigger asChild><Button variant="outline" disabled={finance.pending}>Ações da lista</Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuGroup>
        {editable && <DropdownMenuItem onClick={onEdit}>Editar lista e Pix</DropdownMenuItem>}
        {list.status === "DRAFT" && <DropdownMenuItem onClick={() => onSimple("publish")}>Publicar cobranças</DropdownMenuItem>}
        {open && <DropdownMenuItem onClick={() => onSimple("close")}>Encerrar lista</DropdownMenuItem>}
        {list.status === "CLOSED" && <DropdownMenuItem onClick={() => onReason({ title: "Reabrir lista", description: "A lista voltará a aceitar avisos e recebimentos.", signature: `reopen:${list.id}`, run: command => api.financeListAction(peladaId, list.id, "reopen", command) })}>Reabrir lista</DropdownMenuItem>}
        <DropdownMenuItem onClick={() => onSimple("duplicate")}>Duplicar como rascunho</DropdownMenuItem>
        {editable && <DropdownMenuItem onClick={() => onReason({ title: "Cancelar lista", description: "Só é possível cancelar sem recebimentos válidos nem avisos pendentes. Despesas e outras movimentações permanecem no caixa.", signature: `cancel:${list.id}`, run: command => api.financeListAction(peladaId, list.id, "cancel", command) })}>Cancelar lista</DropdownMenuItem>}
      </DropdownMenuGroup></DropdownMenuContent></DropdownMenu>}
    </div>
    <div className="flex flex-col gap-2"><div className="flex flex-wrap items-center gap-3"><h3 className="break-words text-xl font-semibold">{list.name}</h3><Badge variant="secondary">{financeListStatusLabel[list.status]}</Badge></div>
      {list.description && <p className="whitespace-pre-wrap break-words text-sm text-muted-foreground">{list.description}</p>}
      {list.dailyLabel && <p className="text-sm text-muted-foreground">Diária: {list.dailyLabel}</p>}
      {list.dueDate && <p className="text-sm text-muted-foreground">Vencimento: {list.dueDate.split("-").reverse().join("/")}</p>}
    </div>
    <Card><CardHeader><CardTitle>Arrecadação</CardTitle><CardDescription>{manage || finance.overview?.permissions.viewCash ? "Valores registrados nesta lista" : "Suas cobranças nesta lista"}</CardDescription></CardHeader><CardContent>
      <dl className="grid grid-cols-1 gap-4 md:grid-cols-3">{[["Devido", list.totals.due], ["Recebido", list.totals.received], ["A receber", list.totals.outstanding]].map(([label, amount]) => <div key={label} className="flex justify-between gap-3 md:flex-col"><dt className="text-sm text-muted-foreground">{label}</dt><dd className="text-lg font-semibold tabular-nums">{formatFinanceMoney(amount)}</dd></div>)}</dl>
      {list.totals.pendingNotices > 0 && <p className="mt-4 text-sm">{list.totals.pendingNotices} aviso(s) aguardando conferência</p>}
    </CardContent></Card>
    {(list.pixKey || list.pixRecipient || list.qrId) && <Card><CardHeader><CardTitle>Instruções Pix</CardTitle><CardDescription>{list.pixRecipient || "Recebedor não informado"}</CardDescription></CardHeader><CardContent className="flex flex-col items-start gap-3"><p className="break-all text-sm">{list.pixKey || "Chave não informada"}</p>{list.qrId && <FinanceImage peladaId={peladaId} kind="qr" imageId={list.qrId} />}</CardContent></Card>}
    <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="text-lg font-semibold">Participantes</h3>{editable && <Button variant="outline" onClick={onParticipants} disabled={finance.pending}>Adicionar participantes</Button>}</div>
    {detail.charges.length === 0 ? <p className="py-6 text-muted-foreground">Adicione membros ou convidados para preparar as cobranças.</p> : <ul className="flex flex-col gap-4">{detail.charges.map(charge => {
      const pending = charge.notices.find(n => n.status === "PENDING")
      return <li key={charge.id} className="flex min-w-0 flex-col gap-3 py-4">
        <div className="flex items-start justify-between gap-3"><div className="min-w-0"><p className="break-words font-medium">{charge.name} {charge.userId === null && <span className="text-sm text-muted-foreground">· Convidado</span>}</p>
          <div className="mt-1 flex flex-wrap gap-2"><Badge variant="secondary">{charge.canceled ? "Cancelada" : financePaymentLabel[charge.paymentStatus]}</Badge>{charge.overdue && <Badge variant="destructive">Vencida</Badge>}{pending && <Badge variant="outline">Conferência pendente</Badge>}</div>
        </div><div className="shrink-0 text-right"><p className="font-semibold tabular-nums">{formatFinanceMoney(charge.amountDue)}</p><p className="text-sm text-muted-foreground">Falta {formatFinanceMoney(charge.outstanding)}</p></div></div>
        <p className="break-words text-sm text-muted-foreground">{charge.components.map(c => `${c.name}: ${formatFinanceMoney(c.amount)}`).join(" · ")} · Ajuste: {formatFinanceMoney(charge.adjustment)} · Recebido: {formatFinanceMoney(charge.received)}{charge.dueDate ? ` · Vence ${charge.dueDate.split("-").reverse().join("/")}` : ""}</p>
        <div className="flex flex-wrap gap-2">
          {manage && open && !charge.canceled && !pending && charge.paymentStatus !== "PAID" && <><Button size="sm" disabled={finance.pending} onClick={() => onPayment(charge, "receive")}>Marcar como pago</Button><Button size="sm" variant="outline" disabled={finance.pending} onClick={() => onPayment(charge, "partial")}>Registrar parcial</Button></>}
          {manage && pending && <><Button size="sm" disabled={finance.pending || !open} onClick={() => onPayment(charge, "confirm", pending)}>Confirmar {formatFinanceMoney(pending.amount)}</Button><Button variant="outline" size="sm" disabled={finance.pending} onClick={() => onReason({ title: "Rejeitar aviso", description: "Se o valor recebido for diferente, registre o recebimento correto separadamente após rejeitar.", signature: `reject:${pending.id}`, run: command => api.rejectFinanceNotice(peladaId, list.id, charge.id, pending.id, command) })}>Rejeitar aviso</Button></>}
          {charge.userId === userId && open && !charge.canceled && !pending && charge.paymentStatus !== "PAID" && <Button size="sm" variant="outline" disabled={finance.pending} onClick={() => onPayment(charge, "report")}>Informar pagamento</Button>}
          {manage && <DropdownMenu><DropdownMenuTrigger asChild><Button size="sm" variant="ghost" disabled={finance.pending}>Mais ações</Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuGroup>
            {editable && !charge.canceled && !pending && <><DropdownMenuItem onClick={() => onCharge(charge)}>Ajustar valor ou vencimento</DropdownMenuItem><DropdownMenuItem onClick={() => onReplace(charge)}>Trocar participante</DropdownMenuItem><DropdownMenuItem onClick={() => onReason({ title: "Cancelar cobrança", description: "A cobrança fica preservada no histórico. Resolva avisos e recebimentos antes de cancelar.", signature: `cancel-charge:${charge.id}`, run: command => api.cancelFinanceCharge(peladaId, list.id, charge.id, command) })}>Cancelar cobrança</DropdownMenuItem></>}
            {charge.received !== "0.00" && !pending && <DropdownMenuItem onClick={() => onPayment(charge, "refund")}>Devolver pagamento</DropdownMenuItem>}
          </DropdownMenuGroup></DropdownMenuContent></DropdownMenu>}
        </div>
        {charge.notices.length > 0 && <Collapsible defaultOpen={Boolean(pending)}><CollapsibleTrigger asChild><Button size="sm" variant="ghost">Avisos e decisões</Button></CollapsibleTrigger><CollapsibleContent><FinanceNotices key={finance.overview?.revision} peladaId={peladaId} listId={list.id} chargeId={charge.id} recent={charge.notices} /></CollapsibleContent></Collapsible>}
      </li>
    })}</ul>}
    {editable && <Collapsible><CollapsibleTrigger asChild><Button variant="ghost">Alterar QR Code da lista</Button></CollapsibleTrigger><CollapsibleContent className="pt-3"><FinanceQrForm peladaId={peladaId} finance={finance} listId={list.id} /></CollapsibleContent></Collapsible>}
  </section>
}
