import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field"
import { Switch } from "@/components/ui/switch"
import { SubscriptionStatusBadge } from "@/components/platform/SubscriptionStatusBadge"
import type { SubscriptionFormAction } from "@/schemas/platform"
import type { PlatformSubscription } from "@/types/platform"
import { subscriptionKindLabel, subscriptionPlanLabel } from "@/types/subscription"
import { formatFinanceMoney } from "@/utils/finance"
import { formatBillingDate } from "@/utils/platform"

interface SubscriptionCardProps {
  subscription: PlatformSubscription
  savingFounder: boolean
  onRecordPayment: () => void
  onAction: (action: SubscriptionFormAction) => void
  onFounderChange: (founder: boolean) => void
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-caption text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium tabular-nums">{value}</dd>
    </div>
  )
}

// The state of a pelada's subscription and every action on it
export function SubscriptionCard({ subscription, savingFounder, onRecordPayment, onAction, onFounderChange }: SubscriptionCardProps) {
  // extending keeps the kind, so it only applies while a trial or a paid period exists
  const canExtend = subscription.kind === "TRIAL" || subscription.kind === "PAID"
  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-center justify-between gap-3 space-y-0">
        <CardTitle className="text-title">Assinatura</CardTitle>
        <SubscriptionStatusBadge status={subscription.status} />
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {subscription.pixClaimedAt && (
          <Alert>
            <AlertDescription>
              O criador informou um Pix
              {subscription.claimedPlan && ` do plano ${subscriptionPlanLabel[subscription.claimedPlan].toLowerCase()}`} em{" "}
              {formatBillingDate(subscription.pixClaimedAt)}. Confira e registre o pagamento.
            </AlertDescription>
          </Alert>
        )}
        <dl className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <Row label="Tipo" value={subscriptionKindLabel[subscription.kind]} />
          <Row label="Válida até" value={subscription.validUntil ? formatBillingDate(subscription.validUntil) : "Sem validade"} />
          <Row label="Plano" value={subscription.plan ? subscriptionPlanLabel[subscription.plan] : "—"} />
          <Row
            label="Preço de tabela"
            value={subscription.prices.map((p) => `${formatFinanceMoney(p.amount)} ${subscriptionPlanLabel[p.plan].toLowerCase()}`).join(" · ")}
          />
        </dl>
        <Field orientation="horizontal" className="items-center justify-between gap-4">
          <div className="flex flex-col gap-0.5">
            <FieldLabel htmlFor="platform-founder">Fundadora</FieldLabel>
            <FieldDescription>Preço travado: registre o valor combinado em cada pagamento.</FieldDescription>
          </div>
          <Switch
            id="platform-founder"
            checked={subscription.founder}
            disabled={savingFounder}
            onCheckedChange={onFounderChange}
          />
        </Field>
        <div className="flex flex-wrap gap-2">
          <Button variant="brand" onClick={onRecordPayment}>
            Registrar pagamento
          </Button>
          <Button variant="outline" disabled={!canExtend} onClick={() => onAction("EXTEND_DAYS")}>
            Estender dias
          </Button>
          <Button variant="outline" onClick={() => onAction("SET_VALID_UNTIL")}>
            Definir validade
          </Button>
          <Button variant="outline" disabled={subscription.kind === "EXEMPT"} onClick={() => onAction("EXEMPT")}>
            Isentar
          </Button>
          <Button variant="outline" disabled={subscription.kind === "CANCELED"} onClick={() => onAction("CANCEL")}>
            Cancelar
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
