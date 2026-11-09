import { Link } from "react-router-dom"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import type { PlatformPayment } from "@/types/platform"
import { subscriptionPlanLabel } from "@/types/subscription"
import { formatFinanceMoney } from "@/utils/finance"
import { formatBillingDate, formatIsoDate } from "@/utils/platform"

interface PlatformPaymentsListProps {
  payments: PlatformPayment[]
  // the payments page links each one to its pelada; the pelada's page doesn't
  showPelada?: boolean
  // "Estornar" on the pelada's page
  onVoid?: (payment: PlatformPayment) => void
}

// Recorded payments, newest first; a voided one stays listed with its reason
export function PlatformPaymentsList({ payments, showPelada, onVoid }: PlatformPaymentsListProps) {
  if (payments.length === 0) return <p className="text-sm text-muted-foreground">Nenhum pagamento registrado.</p>
  return (
    <ul className="flex flex-col divide-y divide-border">
      {payments.map((payment) => {
        const voided = payment.voidedAt !== null
        return (
          <li key={payment.id} className="flex flex-wrap items-start justify-between gap-3 py-3 first:pt-0 last:pb-0">
            <div className="flex min-w-0 flex-col gap-0.5">
              <span className="flex flex-wrap items-center gap-2">
                <span className={voided ? "font-semibold tabular-nums text-muted-foreground line-through" : "font-semibold tabular-nums"}>
                  {formatFinanceMoney(payment.amount)}
                </span>
                <span className="text-sm text-muted-foreground">{subscriptionPlanLabel[payment.plan]}</span>
                {voided && (
                  <Badge variant="status" className="bg-destructive-muted text-destructive-soft">
                    Estornado
                  </Badge>
                )}
              </span>
              {showPelada && (
                <span className="text-sm">
                  {payment.peladaId ? (
                    <Link to={`/admin/peladas/${payment.peladaId}`} className="font-medium underline-offset-4 hover:underline">
                      {payment.peladaName}
                    </Link>
                  ) : (
                    <span className="font-medium">{payment.peladaName} (excluída)</span>
                  )}
                  <span className="text-muted-foreground"> · {payment.payerName}</span>
                </span>
              )}
              <span className="text-caption text-muted-foreground">
                Pago em {formatIsoDate(payment.paidOn)} · vale até {formatBillingDate(payment.periodEnd)}
                {payment.recordedBy && ` · registrado por ${payment.recordedBy.username}`}
              </span>
              {payment.note && <span className="text-caption text-muted-foreground">{payment.note}</span>}
              {voided && payment.voidReason && <span className="text-caption text-destructive-soft">Motivo do estorno: {payment.voidReason}</span>}
            </div>
            {onVoid && !voided && (
              <Button variant="outline" size="sm" onClick={() => onVoid(payment)}>
                Estornar
              </Button>
            )}
          </li>
        )
      })}
    </ul>
  )
}
