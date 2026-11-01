import * as api from "@/api/finance"
import type { FinanceCharge, FinanceList, FinanceNotice } from "@/types/finance"
import { financePaymentLabel } from "@/types/finance"
import type { FinancePaymentValues } from "@/schemas/finance"
import type { FinanceReasonAction } from "@/components/finance/FinanceReasonForm"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import { FinanceNotices } from "@/components/finance/FinanceNotices"
import { FinanceImage } from "@/components/finance/FinanceImage"
import { formatFinanceMoney, pendingFinanceNotice } from "@/utils/finance"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet"
import { Separator } from "@/components/ui/separator"

export function FinanceChargeDetails({ peladaId, userId, finance, charge, list, onClose, onPayment, onAdjust, onReplace, onReason }: {
  peladaId: number; userId: number | null; finance: FinanceController; charge: FinanceCharge; list: FinanceList; onClose: () => void
  onPayment: (charge: FinanceCharge, action: FinancePaymentValues["action"], notice?: FinanceNotice) => void
  onAdjust: () => void; onReplace: () => void; onReason: (action: FinanceReasonAction) => void
}) {
  const pending = pendingFinanceNotice(charge)
  const manage = finance.overview?.permissions.manage
  const editable = manage && !charge.canceled && list.status !== "CANCELED" && !pending
  return <Sheet open onOpenChange={open => { if (!open && !finance.pending) onClose() }}><SheetContent className="flex w-full flex-col gap-6 overflow-y-auto sm:max-w-lg">
    <SheetHeader><SheetTitle>{charge.name}</SheetTitle><SheetDescription>{list.name}{charge.userId === null ? " · Convidado" : ""}</SheetDescription></SheetHeader>
    <div className="flex flex-wrap items-center gap-2"><Badge variant="secondary">{charge.canceled ? "Cancelada" : financePaymentLabel[charge.paymentStatus]}</Badge>{charge.overdue && <Badge variant="destructive">Vencida</Badge>}{pending && <Badge variant="outline">Pagamento para conferir</Badge>}</div>
    <dl className="flex flex-col gap-3 text-sm"><div className="flex justify-between gap-3"><dt>Valor da cobrança</dt><dd className="tabular-nums">{formatFinanceMoney(charge.amountDue)}</dd></div><div className="flex justify-between gap-3"><dt>Já recebido</dt><dd className="tabular-nums">{formatFinanceMoney(charge.received)}</dd></div><div className="flex justify-between gap-3"><dt className="font-medium">Falta receber</dt><dd className="font-semibold tabular-nums">{formatFinanceMoney(charge.outstanding)}</dd></div>{charge.dueDate && <div className="flex justify-between gap-3"><dt>Vencimento</dt><dd>{charge.dueDate.split("-").reverse().join("/")}</dd></div>}</dl>
    {pending && <section className="flex flex-col items-start gap-3 rounded-lg bg-muted p-4"><h3 className="text-base font-semibold">Pagamento informado</h3><p className="text-xl font-semibold tabular-nums">{formatFinanceMoney(pending.amount)}</p><p className="text-sm text-muted-foreground">{new Date(pending.createdAt).toLocaleString("pt-BR")}</p>
      {pending.receiptAvailable ? <FinanceImage peladaId={peladaId} kind="receipt" imageId={pending.id} expiresAt={pending.receiptExpiresAt} /> : <p className="text-sm text-muted-foreground">{pending.receiptExpiresAt ? "O comprovante expirou." : "Sem comprovante anexado."}</p>}
      {manage && <div className="flex flex-wrap gap-2"><Button disabled={finance.pending || list.status !== "OPEN"} onClick={() => onPayment(charge, "confirm", pending)}>Confirmar recebimento</Button><Button variant="outline" disabled={finance.pending} onClick={() => onReason({ title: "Rejeitar pagamento informado", description: "Informe por que o pagamento não foi confirmado. O jogador poderá enviar outro aviso.", signature: `reject:${pending.id}`, run: command => api.rejectFinanceNotice(peladaId, list.id, charge.id, pending.id, command) })}>Rejeitar</Button></div>}
    </section>}
    {!pending && !charge.canceled && charge.paymentStatus !== "PAID" && list.status === "OPEN" && <div className="flex flex-wrap gap-2">{manage && <Button disabled={finance.pending} onClick={() => onPayment(charge, "receive")}>Receber pagamento</Button>}{charge.userId === userId && <Button variant="outline" disabled={finance.pending} onClick={() => onPayment(charge, "report")}>Informar meu pagamento</Button>}</div>}
    <Separator />
    <section className="flex flex-col gap-3"><h3 className="text-base font-semibold">Composição do valor</h3><dl className="flex flex-col gap-2 text-sm">{charge.components.map((component, index) => <div key={`${index}:${component.name}`} className="flex justify-between gap-3"><dt>{component.name}</dt><dd className="tabular-nums">{formatFinanceMoney(component.amount)}</dd></div>)}{charge.adjustment !== "0.00" && <div className="flex justify-between gap-3"><dt>Ajuste</dt><dd className="tabular-nums">{formatFinanceMoney(charge.adjustment)}</dd></div>}</dl></section>
    {charge.notices.length > 0 && <section><h3 className="text-base font-semibold">Histórico de pagamentos informados</h3><FinanceNotices key={finance.overview?.revision} peladaId={peladaId} listId={list.id} chargeId={charge.id} recent={charge.notices} /></section>}
    {manage && <><Separator /><section className="flex flex-col items-start gap-2"><h3 className="mb-1 text-base font-semibold">Corrigir cobrança</h3>{pending ? <p className="text-sm text-muted-foreground">Confira o pagamento informado antes de fazer ajustes.</p> : <>
      {editable && <><Button variant="outline" disabled={finance.pending} onClick={onAdjust}>Ajustar valor ou vencimento</Button><Button variant="ghost" disabled={finance.pending} onClick={onReplace}>Trocar participante</Button><Button variant="ghost" disabled={finance.pending} onClick={() => onReason({ title: "Cancelar cobrança", description: "O registro ficará no histórico. Resolva recebimentos antes de cancelar.", signature: `cancel-charge:${charge.id}`, run: command => api.cancelFinanceCharge(peladaId, list.id, charge.id, command) })}>Cancelar cobrança desta pessoa</Button></>}
      {charge.received !== "0.00" && <Button variant="outline" disabled={finance.pending} onClick={() => onPayment(charge, "refund")}>Devolver valor recebido</Button>}
    </>}</section></>}
  </SheetContent></Sheet>
}
