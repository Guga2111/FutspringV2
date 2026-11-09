import { HugeiconsIcon } from "@hugeicons/react"
import { CrownIcon } from "@hugeicons/core-free-icons"
import { SubscriptionStatusBadge } from "@/components/platform/SubscriptionStatusBadge"
import { subscriptionPlanLabel, type PeladaSubscription, type SubscriptionStatus } from "@/types/subscription"
import { formatBillingDate } from "@/utils/platform"

// One line per state: what the creator needs to know about it
const STATUS_TEXT: Record<SubscriptionStatus, (validUntil: string | null) => string> = {
  TRIAL: (end) => (end ? `Teste grátis até ${formatBillingDate(end)}. Assine para continuar depois disso.` : "Teste grátis."),
  ACTIVE: (end) => (end ? `Assinatura em dia até ${formatBillingDate(end)}.` : "Assinatura em dia."),
  PAST_DUE: (end) => (end ? `Venceu em ${formatBillingDate(end)}. Renove para a pelada não travar.` : "Pagamento pendente."),
  READ_ONLY: () => "A pelada está em somente leitura. Assine para voltar a criar sessões e cobranças.",
  PENDING: () => "Esta pelada ainda não tem assinatura: fica em somente leitura até o pagamento.",
  EXEMPT: () => "Esta pelada é isenta: não há nada a pagar.",
}

export function SubscriptionStatusCard({ subscription }: { subscription: PeladaSubscription }) {
  return (
    <section aria-label="Situação da assinatura" className="flex flex-col gap-3 rounded-tile border bg-card p-4 md:p-5">
      <div className="flex flex-wrap items-center gap-2">
        <SubscriptionStatusBadge status={subscription.status} />
        {subscription.plan && subscription.status !== "EXEMPT" && (
          <span className="text-body-sm text-muted-foreground">Plano {subscriptionPlanLabel[subscription.plan].toLowerCase()}</span>
        )}
        {subscription.founder && (
          <span className="flex items-center gap-1 text-body-sm font-medium text-gold">
            <HugeiconsIcon icon={CrownIcon} className="size-4" />
            Fundadora
          </span>
        )}
      </div>
      <p className="text-body">{STATUS_TEXT[subscription.status](subscription.validUntil)}</p>
    </section>
  )
}
