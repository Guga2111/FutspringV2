import { useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Copy01Icon, Share08Icon, WhatsappIcon } from "@hugeicons/core-free-icons"
import { toast } from "sonner"
import type { FinanceDetail } from "@/types/finance"
import { financeWhatsAppMessage, whatsAppUrl, WHATSAPP_URL_MAX } from "@/utils/financeWhatsApp"
import { todayInZone } from "@/utils/dates"
import { areaPath } from "@/utils/peladaAreas"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"

const canShare = typeof navigator !== "undefined" && typeof navigator.share === "function"

// "Compartilhar no WhatsApp": the message is built from the detail on screen when the dialog opens (no request) and can be
// edited before sending; the edit is not saved, reopening builds it again from the current data.
// Plain useState, no react-hook-form: nothing goes to the server and the only rule is "not empty".
export function FinanceWhatsAppDialog({ peladaId, detail, timeZone, onClose }: {
  peladaId: number; detail: FinanceDetail; timeZone: string; onClose: () => void
}) {
  const [original] = useState(() => {
    const link = `${window.location.origin}${areaPath(peladaId, "finance")}/${detail.collection.id}`
    return financeWhatsAppMessage(detail, link, todayInZone(timeZone))
  })
  const [text, setText] = useState(original)
  const empty = text.trim() === ""
  const url = whatsAppUrl(text)
  const tooLong = url.length > WHATSAPP_URL_MAX

  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      toast.success("Mensagem copiada")
    } catch {
      toast.error("Não foi possível copiar")
    }
  }

  // a closed share sheet rejects with AbortError, which is not an error
  async function share() {
    try {
      await navigator.share({ text })
    } catch (error) {
      if (!(error instanceof DOMException && error.name === "AbortError")) toast.error("Não foi possível compartilhar")
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Compartilhar no WhatsApp</DialogTitle>
          <DialogDescription>Revise a mensagem e envie para o grupo. O link leva direto a esta cobrança no app.</DialogDescription>
        </DialogHeader>
        <div className="flex min-w-0 flex-col gap-2">
          <div className="flex min-h-8 items-center justify-between gap-2">
            <Label htmlFor="finance-whatsapp-message">Mensagem</Label>
            {text !== original && <Button variant="ghost" size="sm" onClick={() => setText(original)}>Restaurar texto original</Button>}
          </div>
          {/* grows with the text (field-sizing) up to a maximum height, then scrolls; older browsers keep the rows */}
          <Textarea id="finance-whatsapp-message" rows={10} value={text} onChange={(event) => setText(event.target.value)} className="max-h-[50vh] min-h-40 overflow-y-auto font-sans [field-sizing:content]" />
          {tooLong && <p className="text-body-sm text-muted-foreground">Mensagem longa: use Copiar</p>}
        </div>
        <DialogFooter className="gap-2 sm:space-x-0">
          {canShare && (
            <Button variant="outline" size="icon" disabled={empty} onClick={() => { void share() }} aria-label="Compartilhar">
              <HugeiconsIcon icon={Share08Icon} />
            </Button>
          )}
          <Button variant="outline" disabled={empty} onClick={() => { void copy() }}>
            <HugeiconsIcon icon={Copy01Icon} data-icon="inline-start" className="size-4" />Copiar
          </Button>
          <Button variant="brand" disabled={empty || tooLong} onClick={() => window.open(url, "_blank", "noopener,noreferrer")}>
            <HugeiconsIcon icon={WhatsappIcon} data-icon="inline-start" className="size-4" />Abrir no WhatsApp
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
