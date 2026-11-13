import { z } from "@/lib/zod"
import { normalizeFinanceAmount } from "@/utils/finance"

const note = z.string().max(500, "Máximo de 500 caracteres")
const requiredNote = note.trim().min(1, "Informe uma nota")
const isoDate = z.iso.date("Escolha a data")

// mirrors PlatformRequests.RecordPayment: plan, amount "19.90" (> 0, ≤ 8 integer digits), paidOn not in the future,
// note ≤ 500. The amount accepts "19,90" and "19.90"; `today` is yyyy-MM-dd in the billing zone
export function makeRecordPaymentSchema(today: string) {
  return z.object({
    plan: z.enum(["MONTHLY", "ANNUAL"], "Escolha o plano"),
    amount: z
      .string()
      .refine((value) => {
        const normalized = normalizeFinanceAmount(value)
        return normalized !== null && /^\d{1,8}\.\d{2}$/.test(normalized) && normalized !== "0.00"
      }, "Informe um valor maior que zero, como 19,90")
      .transform((value) => normalizeFinanceAmount(value) ?? value),
    paidOn: isoDate.refine((value) => value <= today, "A data não pode ser no futuro"),
    note,
  })
}
export type RecordPaymentInput = z.input<ReturnType<typeof makeRecordPaymentSchema>>
export type RecordPaymentValues = z.output<ReturnType<typeof makeRecordPaymentSchema>>

// mirrors PlatformRequests.ChangeSubscription for the actions with a form (SET_FOUNDER is a switch, without a note);
// days 1–366, the note is required
export const subscriptionActionSchema = z.discriminatedUnion("action", [
  z.object({ action: z.literal("SET_VALID_UNTIL"), validUntil: isoDate, note: requiredNote }),
  z.object({
    action: z.literal("EXTEND_DAYS"),
    days: z.number("Informe os dias").int("Informe dias inteiros").min(1, "Mínimo de 1 dia").max(366, "Máximo de 366 dias"),
    note: requiredNote,
  }),
  z.object({ action: z.literal("EXEMPT"), note: requiredNote }),
  z.object({ action: z.literal("CANCEL"), note: requiredNote }),
])
export type SubscriptionActionValues = z.infer<typeof subscriptionActionSchema>
export type SubscriptionFormAction = SubscriptionActionValues["action"]

// mirrors PlatformRequests.VoidPayment
export const voidPaymentSchema = z.object({ reason: note.trim().min(1, "Informe o motivo") })
export type VoidPaymentValues = z.infer<typeof voidPaymentSchema>
