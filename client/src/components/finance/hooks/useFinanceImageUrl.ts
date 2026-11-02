import { useEffect, useState } from "react"
import { getFinanceImage } from "@/api/finance"
import { getErrorMessage } from "@/lib/errors"

// A protected finance image (QR Code) as a temporary object URL, revoked when the image or the
// component changes. Results are keyed by image, so another image never shows the previous one.
export function useFinanceImageUrl(peladaId: number, imageId: number | null, enabled = true) {
  const [result, setResult] = useState<{ key: string; url: string | null; error: string | null } | null>(null)
  const key = `${peladaId}:${imageId}`
  useEffect(() => {
    if (!enabled || imageId === null) return
    let canceled = false
    let url: string | null = null
    getFinanceImage(peladaId, "qr", imageId)
      .then((blob) => {
        if (canceled) return
        url = URL.createObjectURL(blob)
        setResult({ key, url, error: null })
      })
      .catch((error) => {
        if (!canceled) setResult({ key, url: null, error: getErrorMessage(error, "Não foi possível carregar o QR Code") })
      })
    return () => {
      canceled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [peladaId, imageId, enabled, key])
  const current = enabled && imageId !== null && result?.key === key ? result : null
  return { url: current?.url ?? null, error: current?.error ?? null, loading: enabled && imageId !== null && current === null }
}
