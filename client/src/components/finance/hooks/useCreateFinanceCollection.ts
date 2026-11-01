import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { createFinanceCollection } from "@/api/finance"
import { financeCreateSchema, type FinanceCreateInput, type FinanceCreateValues } from "@/schemas/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import type { FinanceTemplate } from "@/types/finance"
import { sumFinanceAmounts } from "@/utils/finance"
import { applyServerErrors } from "@/lib/form-errors"

export function useCreateFinanceCollection(peladaId: number, finance: FinanceController, onClose: () => void) {
  const [step, setStep] = useState(0)
  const settings = finance.overview?.settings
  const form = useForm<FinanceCreateInput, unknown, FinanceCreateValues>({ resolver: zodResolver(financeCreateSchema), defaultValues: {
    name: "", classification: "DAILY", amount: "", detailedPrice: false, description: "", dueDate: "", dailyId: null,
    components: [{ name: "Valor por pessoa", amount: "0.00" }], pixKey: settings?.pixKey ?? "", pixRecipient: settings?.pixRecipient ?? "", qrId: settings?.qrId ?? null,
    userIds: [], guestNames: [], reason: "",
  } })
  function template(values: FinanceTemplate) {
    form.reset({ ...form.getValues(), name: values.name, classification: values.classification, amount: sumFinanceAmounts(values.components.map(c => c.amount)),
      detailedPrice: values.components.length > 1, components: values.components, description: values.description ?? "", pixKey: values.pixKey ?? settings?.pixKey ?? "", pixRecipient: values.pixRecipient ?? settings?.pixRecipient ?? "", qrId: values.qrId ?? settings?.qrId ?? null })
  }
  async function next() {
    const valid = await form.trigger(step === 0 ? ["name", "classification", "amount", "components", "description", "dueDate", "dailyId", "pixKey", "pixRecipient"] : ["userIds", "guestNames"])
    if (valid) setStep(s => s + 1)
  }
  async function save(values: FinanceCreateValues, publish: boolean) {
    if (publish && values.userIds.length + values.guestNames.length === 0) {
      form.setError("userIds", { message: "Selecione ao menos uma pessoa para publicar" }); setStep(1); return
    }
    try {
      await finance.mutate(JSON.stringify({ action: "create-collection", values, publish }), "", command => createFinanceCollection(peladaId, command, values, publish))
      toast.success(publish ? "Cobrança criada e publicada" : "Rascunho salvo")
      onClose()
    } catch (error) { applyServerErrors(form, error, "Não foi possível criar a cobrança") }
  }
  return { form, step, setStep, next, template, save }
}
