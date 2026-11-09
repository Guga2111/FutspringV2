import { useEffect } from "react"
import { useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { FinanceFormError, FinanceInput, FinanceSelect } from "@/components/finance/FinanceFields"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { applyServerErrors } from "@/lib/form-errors"
import { makeRecordPaymentSchema, type RecordPaymentInput, type RecordPaymentValues } from "@/schemas/platform"
import type { PlatformSubscription } from "@/types/platform"
import { subscriptionPlanLabel, type SubscriptionPlan } from "@/types/subscription"
import { todayInZone } from "@/utils/dates"
import { BILLING_ZONE } from "@/utils/platform"

const PLAN_OPTIONS = (Object.keys(subscriptionPlanLabel) as SubscriptionPlan[]).map((plan) => ({ value: plan, label: subscriptionPlanLabel[plan] }))

interface RecordPaymentSheetProps {
  subscription: PlatformSubscription
  onSubmit: (values: RecordPaymentValues) => Promise<void>
  onClose: () => void
}

// "Registrar pagamento": the plan (the one the creator claimed, else the current one), the amount filled with the list
// price of that plan until it's edited, the payment date (today in São Paulo) and a note
export function RecordPaymentSheet({ subscription, onSubmit, onClose }: RecordPaymentSheetProps) {
  const today = todayInZone(BILLING_ZONE)
  const price = (plan: SubscriptionPlan) => subscription.prices.find((p) => p.plan === plan)?.amount ?? ""
  const plan = subscription.claimedPlan ?? subscription.plan ?? "MONTHLY"
  const form = useForm<RecordPaymentInput, unknown, RecordPaymentValues>({
    resolver: zodResolver(makeRecordPaymentSchema(today)),
    defaultValues: { plan, amount: price(plan), paidOn: today, note: "" },
  })
  const selected = useWatch({ control: form.control, name: "plan" }) as SubscriptionPlan

  // another plan brings its price, unless the amount was typed (a founder's locked price)
  useEffect(() => {
    if (!form.getFieldState("amount").isDirty) form.setValue("amount", price(selected))
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only when the plan changes
  }, [selected])

  async function submit(values: RecordPaymentValues) {
    try {
      await onSubmit(values)
      onClose()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível registrar o pagamento")
    }
  }

  const busy = form.formState.isSubmitting
  return (
    <Sheet open onOpenChange={(open) => !open && !busy && onClose()}>
      <SheetContent className="flex w-full flex-col gap-5 overflow-y-auto sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Registrar pagamento</SheetTitle>
          <SheetDescription>
            A validade passa a contar do fim da atual (ou de hoje, se já venceu) mais um mês ou um ano.
          </SheetDescription>
        </SheetHeader>
        <form noValidate className="flex flex-col gap-5" onSubmit={form.handleSubmit(submit)}>
          <FieldGroup>
            <FinanceSelect control={form.control} name="plan" label="Plano" options={PLAN_OPTIONS} />
            <FinanceInput control={form.control} name="amount" label="Valor cobrado (R$)" />
            <FinanceInput control={form.control} name="paidOn" label="Data do pagamento" type="date" />
            <FinanceInput control={form.control} name="note" label="Nota (opcional)" multiline />
          </FieldGroup>
          <FinanceFormError message={form.formState.errors.root?.message} />
          <Button variant="brand" type="submit" disabled={busy}>
            Registrar pagamento
          </Button>
        </form>
      </SheetContent>
    </Sheet>
  )
}
