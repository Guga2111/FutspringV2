import { z } from "zod"
import { normalizeFinanceAmount, sumFinanceAmounts } from "@/utils/finance"

export const financeMoneySchema = z.string().regex(/^\d{1,12}\.\d{2}$/, "Use duas casas decimais, por exemplo 45.00")
export const financeSignedMoneySchema = z.string().regex(/^-?\d{1,12}\.\d{2}$/, "Use duas casas decimais, por exemplo -5.00")
const reason = z.string().max(1000, "Máximo de 1000 caracteres")
const requiredReason = reason.trim().min(1, "Informe o motivo")
const id = z.number().int().positive()
const date = z.union([z.literal(""), z.iso.date()])
const component = z.object({ name: z.string().trim().min(1, "Informe o componente").max(100), amount: financeMoneySchema })
const components = z.array(component).min(1).max(20)
const method = z.enum(["PIX", "CASH", "TRANSFER", "OTHER"])
// Mirrors FinanceRequests.Command; the UI generates requestKey once per logical submission.
export const financeCommandSchema = z.object({ revision: z.number().int().min(0), requestKey: z.uuid(), reason: reason.nullable() })
// FinanceRequests.Collection and Template (UI keeps optional fields as empty strings).
export const financeListSchema = z.object({
  name: z.string().trim().min(1, "Informe o nome").max(200), classification: z.enum(["DAILY", "MONTHLY", "OTHER"]),
  description: z.string().max(2000), dueDate: date, dailyId: id.nullable(), components,
  pixKey: z.string().max(200), pixRecipient: z.string().max(200), qrId: id.nullable(), reason,
})
export const financeTemplateSchema = financeListSchema.omit({ dueDate: true, dailyId: true }).extend({ archived: z.boolean() })
// FinanceRequests.Charge: participant identity is chosen by the surrounding action.
export const financeChargeSchema = z.object({ components, adjustment: financeSignedMoneySchema, dueDate: date, reason })
// FinanceRequests.Participants: imported attendance is only a selection suggestion.
export const financeParticipantsSchema = z.object({ userIds: z.array(id).max(100), guestNames: z.array(z.string().trim().min(1).max(200)).max(100), reason })
  .refine(v => v.userIds.length + v.guestNames.length > 0, { message: "Selecione participantes", path: ["userIds"] })
// FinanceRequests.Receipt / Confirmation / Refund.
export const financeReceiptSchema = z.object({ amount: financeMoneySchema.refine(v => Number(v) > 0, "Informe um valor maior que zero"), effectiveDate: date, method, reason })
export const financeRefundSchema = financeReceiptSchema.extend({ reason: requiredReason, reduceObligation: z.boolean() })
export const financeConfirmationSchema = financeReceiptSchema.omit({ amount: true })
export const financeReasonSchema = z.object({ reason: requiredReason })
// FinanceRequests.Cash. Positive ordinary entries; the opening balance may be negative.
export const financeCashSchema = z.object({ kind: z.enum(["OPENING", "INCOME", "EXPENSE"]), amount: financeSignedMoneySchema,
  description: z.string().trim().min(1, "Informe a descrição").max(500), category: z.string().max(100), effectiveDate: date,
  method, listId: id.nullable(), dailyId: id.nullable(), reason,
}).superRefine((v, ctx) => {
  if (v.kind !== "OPENING" && Number(v.amount) <= 0) ctx.addIssue({ code: "custom", path: ["amount"], message: "Informe um valor maior que zero" })
  if (v.kind === "OPENING" && !v.reason.trim()) ctx.addIssue({ code: "custom", path: ["reason"], message: "Informe o motivo do saldo inicial" })
  if (v.kind === "EXPENSE" && !v.category.trim()) ctx.addIssue({ code: "custom", path: ["category"], message: "Informe a categoria" })
})
// FinanceRequests.Settings / Managers.
export const financeSettingsSchema = z.object({ visibility: z.enum(["INDIVIDUAL", "TRANSPARENT"]), pixKey: z.string().max(200), pixRecipient: z.string().max(200), qrId: id.nullable() })
export const financeManagersSchema = z.object({ userIds: z.array(id).max(100) })
export const financeImageSchema = z.file().max(5 * 1024 * 1024, "Máximo de 5 MB").mime(["image/jpeg", "image/png"], "Use JPEG ou PNG")
export const financeReportSchema = z.object({ file: financeImageSchema.nullable() })
export const financeQrSchema = z.object({ file: financeImageSchema, reason })
export type FinanceCommand = z.infer<typeof financeCommandSchema>
export type FinanceListValues = z.infer<typeof financeListSchema>
export type FinanceTemplateValues = z.infer<typeof financeTemplateSchema>
export type FinanceChargeValues = z.infer<typeof financeChargeSchema>
export type FinanceParticipantsValues = z.infer<typeof financeParticipantsSchema>
export type FinanceReceiptValues = z.infer<typeof financeReceiptSchema>
export type FinanceRefundValues = z.infer<typeof financeRefundSchema>
export type FinanceCashValues = z.infer<typeof financeCashSchema>
// One payment sheet renders the explicit receipt/confirmation/refund/report commands.
export const financePaymentFormSchema = z.object({
  action: z.enum(["receive", "partial", "confirm", "refund", "report"]), amount: z.string(), effectiveDate: date,
  method, reason, reduceObligation: z.boolean(), file: financeImageSchema.nullable(),
}).superRefine((v, ctx) => {
  if (["receive", "partial", "refund"].includes(v.action) && (!financeMoneySchema.safeParse(v.amount).success || Number(v.amount) <= 0))
    ctx.addIssue({ code: "custom", path: ["amount"], message: "Informe um valor positivo com duas casas decimais" })
  if (v.action === "refund" && !v.reason.trim()) ctx.addIssue({ code: "custom", path: ["reason"], message: "Informe o motivo" })
})
export type FinancePaymentValues = z.infer<typeof financePaymentFormSchema>
export const financeReplacementSchema = financeReasonSchema.extend({ userId: id.nullable(), guestName: z.string().max(200) })
  .refine(v => (v.userId === null) !== !v.guestName.trim(), { message: "Selecione um membro ou informe um convidado", path: ["guestName"] })
// UI inputs accept pt-BR amounts; API schemas above continue requiring exact decimal strings.
export const financeAmountInputSchema = z.string().refine(value => {
  const normalized = normalizeFinanceAmount(value)
  return normalized !== null && !normalized.startsWith("-")
}, "Informe um valor válido, como 45,00").transform(value => normalizeFinanceAmount(value) ?? value)
export const financeSignedAmountInputSchema = z.string().refine(value => normalizeFinanceAmount(value) !== null, "Informe um valor válido, como -5,00").transform(value => normalizeFinanceAmount(value) ?? value)
export const financeCreateSchema = financeListSchema.extend({
  amount: z.string(),
  detailedPrice: z.boolean(),
  components: z.array(z.object({ name: z.string(), amount: z.string() })).min(1).max(20),
  userIds: z.array(id).max(100, "Selecione até 100 jogadores"),
  guestNames: z.array(z.string().trim().max(200, "O nome pode ter até 200 caracteres")).max(100, "Adicione até 100 convidados").transform(names => names.filter(Boolean)),
}).superRefine((values, ctx) => {
  if (values.detailedPrice) {
    const parsed = z.array(z.object({ name: component.shape.name, amount: financeAmountInputSchema })).safeParse(values.components)
    if (!parsed.success) for (const issue of parsed.error.issues) ctx.addIssue({ ...issue, path: ["components", ...issue.path] })
  }
  const amount = values.detailedPrice ? sumFinanceAmounts(values.components.map(c => c.amount)) : normalizeFinanceAmount(values.amount)
  if (!amount || !financeMoneySchema.safeParse(amount).success || amount === "0.00")
    ctx.addIssue({ code: "custom", path: [values.detailedPrice ? "components" : "amount"], message: "Informe um valor maior que zero" })
}).transform(values => {
  const amount = values.detailedPrice ? sumFinanceAmounts(values.components.map(c => c.amount)) : normalizeFinanceAmount(values.amount) ?? values.amount
  return { ...values, amount, components: values.detailedPrice ? values.components.map(c => ({ name: c.name.trim(), amount: normalizeFinanceAmount(c.amount) ?? c.amount })) : [{ name: "Valor por pessoa", amount }] }
})
export type FinanceCreateInput = z.input<typeof financeCreateSchema>
export type FinanceCreateValues = z.output<typeof financeCreateSchema>
export const financeReceiveFormSchema = z.object({
  action: z.enum(["receive", "partial", "confirm", "refund", "report"]),
  amount: financeAmountInputSchema,
  effectiveDate: date, method, reason, reduceObligation: z.boolean(), file: financeImageSchema.nullable(),
}).superRefine((values, ctx) => {
  if (values.action !== "report" && values.amount === "0.00") ctx.addIssue({ code: "custom", path: ["amount"], message: "Informe um valor maior que zero" })
  if (values.action === "refund" && !values.reason.trim()) ctx.addIssue({ code: "custom", path: ["reason"], message: "Informe o motivo" })
})

export const financeListFormSchema = financeListSchema.extend({ components: z.array(z.object({ name: component.shape.name, amount: financeAmountInputSchema })).min(1).max(20) })
export const financeChargeFormSchema = financeChargeSchema.extend({ components: financeListFormSchema.shape.components, adjustment: financeSignedAmountInputSchema })
export const financeCashFormSchema = financeCashSchema.safeExtend({ amount: financeSignedAmountInputSchema })

export const financePeopleFormSchema = z.object({ userIds: z.array(id).max(100), guestNames: z.array(z.string().trim().max(200)).max(100).transform(names => names.filter(Boolean)), reason })
  .refine(values => values.userIds.length + values.guestNames.length > 0, { message: "Selecione ao menos uma pessoa", path: ["userIds"] })
