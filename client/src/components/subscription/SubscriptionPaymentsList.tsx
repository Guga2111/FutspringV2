import { subscriptionPlanLabel, type CreatorSubscriptionPayment } from "@/types/subscription"
import { formatFinanceMoney } from "@/utils/finance"
import { formatBillingDate, formatIsoDate } from "@/utils/platform"

export function SubscriptionPaymentsList({ payments }: { payments: CreatorSubscriptionPayment[] }) {
  return (
    <section aria-labelledby="subscription-payments" className="flex flex-col gap-3">
      <h2 id="subscription-payments" className="text-title font-semibold">Pagamentos anteriores</h2>
      {payments.length === 0 ? (
        <p className="rounded-tile border bg-card p-4 text-body-sm text-muted-foreground">Nenhum pagamento ainda.</p>
      ) : (
        <ul className="divide-y rounded-tile border bg-card">
          {payments.map((payment) => (
            <li key={`${payment.paidOn}-${payment.periodEnd}`} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <p className="text-body font-medium">
                  {formatIsoDate(payment.paidOn)} · {subscriptionPlanLabel[payment.plan]}
                </p>
                <p className="text-caption text-muted-foreground">Válido até {formatBillingDate(payment.periodEnd)}</p>
              </div>
              <span className="shrink-0 text-body font-semibold tabular-nums">{formatFinanceMoney(payment.amount)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
