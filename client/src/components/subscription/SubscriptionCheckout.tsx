import { useState } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { HugeiconsIcon } from "@hugeicons/react"
import { CheckmarkCircle02Icon, Clock01Icon, Copy01Icon } from "@hugeicons/core-free-icons"
import { QRCodeSVG } from "qrcode.react"
import { toast } from "sonner"
import { claimPix } from "@/api/subscription"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { applyServerErrors } from "@/lib/form-errors"
import { pixClaimSchema, type PixClaimValues } from "@/schemas/subscription"
import { subscriptionPlanLabel, type PeladaSubscription, type SubscriptionPlan } from "@/types/subscription"
import { formatFinanceMoney } from "@/utils/finance"
import { formatBillingDate } from "@/utils/platform"

interface SubscriptionCheckoutProps {
  peladaId: number
  subscription: PeladaSubscription
  onClaimed: (subscription: PeladaSubscription) => void
}

const PERIOD: Record<SubscriptionPlan, string> = { MONTHLY: "por mês", ANNUAL: "por ano" }

// Pick a plan, pay its Pix (QR Code or copy and paste) and tell the platform: "Já fiz o Pix"
export function SubscriptionCheckout({ peladaId, subscription, onClaimed }: SubscriptionCheckoutProps) {
  const [copied, setCopied] = useState(false)
  const form = useForm<PixClaimValues>({
    resolver: zodResolver(pixClaimSchema),
    defaultValues: { plan: subscription.claimedPlan ?? subscription.plan ?? "MONTHLY" },
  })
  const plan = useWatch({ control: form.control, name: "plan" })
  const offer = subscription.plans.find((p) => p.plan === plan)
  const pixAvailable = subscription.pixRecipient !== null
  const alreadyClaimed = subscription.pixClaimedAt !== null && subscription.claimedPlan === plan

  async function copyCode() {
    if (!offer?.pixCode) return
    try {
      await navigator.clipboard.writeText(offer.pixCode)
      setCopied(true)
      toast.success("Código Pix copiado")
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error("Não foi possível copiar. Selecione o código e copie manualmente.")
    }
  }

  async function onSubmit(values: PixClaimValues) {
    try {
      const saved = await claimPix(peladaId, values)
      form.reset(values)
      onClaimed(saved)
      toast.success("Pix informado. Vamos confirmar o pagamento em breve.")
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível informar o Pix")
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5">
      <section aria-labelledby="subscription-plans" className="flex flex-col gap-3">
        <h2 id="subscription-plans" className="text-title font-semibold">Escolha o plano</h2>
        <Controller
          name="plan"
          control={form.control}
          render={({ field }) => (
            <RadioGroup value={field.value} onValueChange={field.onChange} className="grid gap-3 md:grid-cols-2" aria-label="Plano">
              {subscription.plans.map((option) => (
                <FieldLabel
                  key={option.plan}
                  htmlFor={`subscription-plan-${option.plan}`}
                  className="flex w-full cursor-pointer items-center gap-3 rounded-xl border bg-card p-4 font-normal has-[[data-state=checked]]:border-brand has-[[data-state=checked]]:bg-brand-muted"
                >
                  <RadioGroupItem value={option.plan} id={`subscription-plan-${option.plan}`} />
                  <span className="flex flex-col">
                    <span className="font-medium">{subscriptionPlanLabel[option.plan]}</span>
                    <span className="text-sm text-muted-foreground">
                      <span className="font-display text-heading font-bold tabular-nums text-foreground">{formatFinanceMoney(option.amount)}</span>{" "}
                      {PERIOD[option.plan]}
                    </span>
                  </span>
                </FieldLabel>
              ))}
            </RadioGroup>
          )}
        />
      </section>

      {!pixAvailable ? (
        <Alert>
          <AlertDescription>Pagamento por Pix indisponível no momento. Fale com o suporte.</AlertDescription>
        </Alert>
      ) : (
        offer?.pixCode && (
          <section aria-label="Pagamento por Pix" className="flex flex-col gap-5 rounded-tile border bg-card p-4 sm:flex-row sm:items-center md:p-5">
            {/* QR Codes need a light quiet zone to scan, so the frame is white in both themes */}
            <div className="mx-auto shrink-0 rounded-hero bg-paper p-3 text-night sm:mx-0">
              <QRCodeSVG value={offer.pixCode} size={168} fgColor="currentColor" bgColor="transparent" title="QR Code Pix" />
            </div>
            <div className="flex min-w-0 flex-1 flex-col gap-3">
              <div>
                <p className="font-display text-score font-bold tabular-nums">{formatFinanceMoney(offer.amount)}</p>
                <p className="text-caption text-muted-foreground">Abra o app do banco e escaneie o QR Code, ou copie o código.</p>
              </div>
              <div className="flex flex-col gap-1.5">
                <FieldLabel htmlFor="subscription-pix-code" className="text-caption font-normal text-muted-foreground">
                  Pix Copia e Cola
                </FieldLabel>
                <div className="flex items-center gap-2">
                  <Input id="subscription-pix-code" readOnly value={offer.pixCode} className="min-w-0 flex-1 font-mono text-caption" onFocus={(e) => e.currentTarget.select()} />
                  <Button type="button" size="sm" variant="brand" onClick={copyCode}>
                    <HugeiconsIcon icon={copied ? CheckmarkCircle02Icon : Copy01Icon} />
                    {copied ? "Copiado" : "Copiar"}
                  </Button>
                </div>
              </div>
              <p className="text-sm text-muted-foreground">
                Recebedor: <span className="font-medium text-foreground">{subscription.pixRecipient}</span>
              </p>
            </div>
          </section>
        )
      )}

      {subscription.pixClaimedAt && (
        <p className="flex items-center gap-2 text-body-sm text-muted-foreground">
          <HugeiconsIcon icon={Clock01Icon} className="size-4 shrink-0" />
          Pix {subscription.claimedPlan ? subscriptionPlanLabel[subscription.claimedPlan].toLowerCase() : ""} informado em{" "}
          {formatBillingDate(subscription.pixClaimedAt)} · aguardando confirmação
        </p>
      )}

      {form.formState.errors.root && (
        <Alert variant="destructive">
          <AlertDescription>{form.formState.errors.root.message}</AlertDescription>
        </Alert>
      )}

      {pixAvailable && (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <Button type="submit" variant="brand" disabled={form.formState.isSubmitting || alreadyClaimed}>
            Já fiz o Pix
          </Button>
          <p className="text-caption text-muted-foreground">
            {alreadyClaimed ? "Já recebemos o seu aviso deste plano." : "Avise depois de pagar: a confirmação é feita à mão."}
          </p>
        </div>
      )}
    </form>
  )
}
