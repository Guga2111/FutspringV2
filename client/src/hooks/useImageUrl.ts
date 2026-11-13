import { useEffect, useState } from "react"
import { getImage } from "@/api/files"
import { useAuth } from "@/hooks/useAuth"

// Private bytes travel through the Bearer interceptor. Object URLs live only while this account/image is mounted.
export function useImageUrl(filename: string | null | undefined): string | undefined {
  const { user } = useAuth()
  const userId = user?.id
  const key = `${userId}:${filename}`
  const [result, setResult] = useState<{ key: string; url: string } | null>(null)
  useEffect(() => {
    if (!filename || !userId) return
    let canceled = false
    let url: string | undefined
    getImage(filename).then((blob) => {
      if (canceled) return
      url = URL.createObjectURL(blob)
      setResult({ key, url })
    }).catch(() => { /* An inaccessible or deleted image uses the component's fallback. */ })
    return () => {
      canceled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [filename, userId, key])
  return userId && filename && result?.key === key ? result.url : undefined
}
