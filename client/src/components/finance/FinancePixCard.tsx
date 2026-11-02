import { useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { CheckmarkCircle02Icon, Copy01Icon, QrCodeIcon } from "@hugeicons/core-free-icons"
import { toast } from "sonner"
import { useFinanceImageUrl } from "@/components/finance/hooks/useFinanceImageUrl"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { cn } from "@/lib/utils"
import { formatFinanceMoney } from "@/utils/finance"

interface FinancePixCardProps {
  peladaId: number
  pixKey: string | null
  pixRecipient: string | null
  qrId: number | null
  // what the viewer still has to pay; omitted for managers
  amount?: string | null
  className?: string
}

// How to pay a collection: the QR Code on screen (tap to enlarge), the Pix key with a copy button and the recipient.
// Renders nothing when the collection has neither a key nor a QR Code.
export function FinancePixCard({ peladaId, pixKey, pixRecipient, qrId, amount, className }: FinancePixCardProps) {
  const [zoom, setZoom] = useState(false)
  const [copied, setCopied] = useState(false)
  const qr = useFinanceImageUrl(peladaId, qrId)
  if (!pixKey && qrId === null) return null

  async function copyKey() {
    if (!pixKey) return
    try {
      await navigator.clipboard.writeText(pixKey)
      setCopied(true)
      toast.success("Chave Pix copiada")
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error("Não foi possível copiar. Selecione a chave e copie manualmente.")
    }
  }

  return (
    <section
      aria-label="Pagamento por Pix"
      className={cn("flex flex-col gap-5 rounded-tile border bg-card p-4 sm:flex-row sm:items-center md:p-5", className)}
    >
      {qrId !== null && (
        <div className="flex shrink-0 flex-col items-center gap-2">
          {/* QR Codes need a light quiet zone to scan, so the frame is white in both themes */}
          <Button
            type="button"
            variant="ghost"
            className="h-auto rounded-xl bg-white p-2.5 shadow-sm ring-1 ring-border hover:bg-white"
            onClick={() => setZoom(true)}
            disabled={!qr.url}
            aria-label="Ampliar QR Code"
          >
            {qr.url ? (
              <img src={qr.url} alt="QR Code Pix" width={144} height={144} className="size-36 object-contain" />
            ) : qr.error ? (
              <span className="flex size-36 items-center justify-center whitespace-normal p-2 text-center text-xs text-destructive">{qr.error}</span>
            ) : (
              <Skeleton className="size-36" />
            )}
          </Button>
          {qr.url && <span className="text-caption text-muted-foreground">Toque para ampliar</span>}
        </div>
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-3">
        <p className="flex items-center gap-1.5 text-overline uppercase text-brand-text">
          <HugeiconsIcon icon={QrCodeIcon} className="size-4" />
          Pague com Pix
        </p>
        {amount && (
          <div>
            <p className="font-display text-score font-bold tabular-nums">{formatFinanceMoney(amount)}</p>
            <p className="text-caption text-muted-foreground">falta pagar</p>
          </div>
        )}
        {pixKey && (
          <div className="flex flex-col gap-1.5">
            <span className="text-caption text-muted-foreground">Chave Pix</span>
            <div className="flex items-center gap-2 rounded-lg border bg-card-elevated py-1.5 pl-3 pr-1.5">
              <span className="min-w-0 flex-1 select-all break-all text-sm font-medium">{pixKey}</span>
              <Button type="button" size="sm" variant="brand" onClick={copyKey}>
                <HugeiconsIcon icon={copied ? CheckmarkCircle02Icon : Copy01Icon} />
                {copied ? "Copiada" : "Copiar"}
              </Button>
            </div>
          </div>
        )}
        {pixRecipient && (
          <p className="text-sm text-muted-foreground">
            Recebedor: <span className="font-medium text-foreground">{pixRecipient}</span>
          </p>
        )}
      </div>
      <Dialog open={zoom} onOpenChange={setZoom}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>QR Code Pix</DialogTitle>
            <DialogDescription>Abra o app do seu banco e escaneie o código.</DialogDescription>
          </DialogHeader>
          {qr.url && (
            <div className="rounded-xl bg-white p-4">
              <img src={qr.url} alt="QR Code Pix ampliado" className="h-auto w-full object-contain" />
            </div>
          )}
        </DialogContent>
      </Dialog>
    </section>
  )
}
