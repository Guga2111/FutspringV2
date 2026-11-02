import { useRef, useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Delete02Icon, QrCodeIcon, Upload04Icon } from "@hugeicons/core-free-icons"
import { useFinanceImageUrl } from "@/components/finance/hooks/useFinanceImageUrl"
import { FinanceFormError } from "@/components/finance/FinanceFields"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { financeImageSchema } from "@/schemas/finance"
import { cn } from "@/lib/utils"

interface FinanceQrFieldProps {
  peladaId: number
  qrId: number | null
  busy: boolean
  // called with a valid JPEG/PNG up to 5 MB, as soon as it is picked
  onUpload: (file: File) => Promise<void> | void
  onRemove?: () => Promise<void> | void
  // without the preview, when the QR Code is already on screen (the collection's Pix card)
  preview?: boolean
}

// The QR Code of a Pix instruction: a preview of the current image and Enviar / Trocar / Remover.
// The file is validated and sent on pick (no extra Salvar), like the pelada photo.
export function FinanceQrField({ peladaId, qrId, busy, onUpload, onRemove, preview = true }: FinanceQrFieldProps) {
  const input = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const qr = useFinanceImageUrl(peladaId, qrId, preview)

  async function pick(file: File | undefined) {
    if (input.current) input.current.value = ""
    if (!file) return
    const parsed = financeImageSchema.safeParse(file)
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Imagem inválida")
      return
    }
    setError(null)
    await onUpload(file)
  }

  return (
    <div className="flex flex-col gap-3">
      <span className="text-sm font-medium">{preview ? "QR Code" : "QR Code desta cobrança"}</span>
      <div className="flex flex-wrap items-center gap-4">
        {/* an image sits on white in both themes (QR Codes need a light quiet zone to scan) */}
        {preview && (
          <div
            className={cn(
              "flex size-28 shrink-0 items-center justify-center overflow-hidden rounded-xl p-2",
              qrId === null ? "border border-dashed border-input bg-card-elevated" : "bg-white ring-1 ring-border",
            )}
          >
            {qrId === null ? (
              <span className="flex flex-col items-center gap-1 text-center text-xs text-muted-foreground">
                <HugeiconsIcon icon={QrCodeIcon} className="size-6" />
                Sem QR Code
              </span>
            ) : qr.url ? (
              <img src={qr.url} alt="QR Code Pix atual" width={96} height={96} className="size-24 object-contain" />
            ) : qr.error ? (
              <span className="text-center text-xs text-destructive">{qr.error}</span>
            ) : (
              <Skeleton className="size-24" />
            )}
          </div>
        )}
        <div className="flex flex-col items-start gap-2">
          <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => input.current?.click()}>
            <HugeiconsIcon icon={Upload04Icon} />
            {qrId === null ? "Enviar QR Code" : "Trocar imagem"}
          </Button>
          {qrId !== null && onRemove && (
            <Button type="button" variant="ghost" size="sm" disabled={busy} onClick={() => void onRemove()}>
              <HugeiconsIcon icon={Delete02Icon} />
              Remover
            </Button>
          )}
          <p className="text-caption text-muted-foreground">A imagem que o banco gera para a sua chave. JPEG ou PNG, até 5 MB.</p>
        </div>
      </div>
      <Input
        ref={input}
        type="file"
        accept="image/jpeg,image/png"
        className="hidden"
        aria-label="Imagem do QR Code"
        onChange={(event) => void pick(event.target.files?.[0])}
      />
      <FinanceFormError message={error ?? undefined} />
    </div>
  )
}
