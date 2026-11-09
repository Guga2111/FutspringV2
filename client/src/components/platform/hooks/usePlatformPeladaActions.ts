import { useState } from "react"
import { toast } from "sonner"
import { changePlatformSubscription, getPlatformPelada, recordPlatformPayment, voidPlatformPayment } from "@/api/platform"
import { getErrorMessage } from "@/lib/errors"
import type { RecordPaymentValues, SubscriptionActionValues } from "@/schemas/platform"
import type { PlatformPeladaDetail } from "@/types/platform"

const ACTION_DONE: Record<SubscriptionActionValues["action"], string> = {
  SET_VALID_UNTIL: "Validade definida",
  EXTEND_DAYS: "Dias estendidos",
  EXEMPT: "Pelada isentada",
  CANCEL: "Assinatura cancelada",
}

// The commands of a pelada's page in the panel. Each answer is the new detail, merged as it comes; the forms get the
// errors back (applyServerErrors), the founder switch shows them in a toast.
export function usePlatformPeladaActions(peladaId: number, setDetail: (update: (detail: PlatformPeladaDetail) => PlatformPeladaDetail) => void) {
  const [savingFounder, setSavingFounder] = useState(false)
  const replace = (detail: PlatformPeladaDetail) => setDetail(() => detail)

  async function recordPayment(values: RecordPaymentValues) {
    replace(await recordPlatformPayment(peladaId, { ...values, note: values.note.trim() || null }))
    toast.success("Pagamento registrado")
  }

  async function changeSubscription(values: SubscriptionActionValues) {
    replace(await changePlatformSubscription(peladaId, { ...values, note: values.note.trim() }))
    toast.success(ACTION_DONE[values.action])
  }

  async function setFounder(founder: boolean) {
    setSavingFounder(true)
    try {
      replace(await changePlatformSubscription(peladaId, { action: "SET_FOUNDER", founder, note: null }))
      toast.success(founder ? "Marcada como fundadora" : "Fundadora desmarcada")
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível alterar a fundadora"))
    } finally {
      setSavingFounder(false)
    }
  }

  // the void answers the payment only: merged at once, then the detail is read again for the event it recorded
  async function voidPayment(paymentId: number, reason: string) {
    const voided = await voidPlatformPayment(paymentId, reason.trim())
    setDetail((detail) => ({ ...detail, payments: detail.payments.map((p) => (p.id === voided.id ? voided : p)) }))
    toast.success("Pagamento estornado")
    getPlatformPelada(peladaId).then(replace, () => {})
  }

  return { recordPayment, changeSubscription, setFounder, savingFounder, voidPayment }
}
