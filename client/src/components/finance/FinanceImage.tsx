import { useEffect, useState } from "react"
import { getFinanceImage } from "@/api/finance"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { FinanceFormError } from "@/components/finance/FinanceFields"
import { getErrorMessage } from "@/lib/errors"

export function FinanceImage({ peladaId, kind, imageId, expiresAt }: { peladaId: number; kind: "qr" | "receipt"; imageId: number; expiresAt?: string | null }) {
  const [open, setOpen] = useState(false)
  const [url, setUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    if (!open) return
    let canceled = false
    let imageUrl: string | null = null
    let timer: ReturnType<typeof setTimeout> | undefined
    if (expiresAt) {
      const expire = () => {
        const remaining = new Date(expiresAt).getTime() - Date.now()
        if (remaining > 0) timer = setTimeout(expire, Math.min(remaining, 2_147_483_647))
        else { setOpen(false); setUrl(null) }
      }
      expire()
    }
    getFinanceImage(peladaId, kind, imageId).then(blob => {
      if (!canceled) { imageUrl = URL.createObjectURL(blob); setUrl(imageUrl) }
    }).catch(e => { if (!canceled) setError(getErrorMessage(e, "Não foi possível abrir a imagem")) })
    return () => { canceled = true; if (imageUrl) URL.revokeObjectURL(imageUrl); clearTimeout(timer) }
  }, [open, peladaId, kind, imageId, expiresAt])
  return <><Button variant="outline" size="sm" onClick={() => { setUrl(null); setError(null); setOpen(true) }}>Abrir {kind === "qr" ? "QR Code" : "comprovante"}</Button>
    <Dialog open={open} onOpenChange={setOpen}><DialogContent className="max-h-[90dvh] overflow-y-auto"><DialogHeader><DialogTitle>{kind === "qr" ? "QR Code Pix" : "Comprovante de pagamento"}</DialogTitle></DialogHeader>
      {error ? <FinanceFormError message={error} /> : url ? <img src={url} alt={kind === "qr" ? "QR Code fornecido pelo banco" : "Comprovante enviado pelo jogador"} className="h-auto max-h-[70dvh] w-full object-contain" /> : <Skeleton className="h-64 w-full" />}
    </DialogContent></Dialog></>
}
