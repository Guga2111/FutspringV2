import { useState } from "react"
import { toast } from "sonner"
import { exportMyData } from "@/api/users"
import { getErrorMessage } from "@/lib/errors"
import { todayInZone } from "@/utils/dates"

// "Baixar meus dados": fetches the export and saves it as futspring-meus-dados-2026-11-09.json
export function useAccountExport() {
  const [pending, setPending] = useState(false)

  async function download() {
    setPending(true)
    try {
      const blob = await exportMyData()
      const url = URL.createObjectURL(blob)
      const link = document.createElement("a")
      link.href = url
      link.download = `futspring-meus-dados-${todayInZone(Intl.DateTimeFormat().resolvedOptions().timeZone)}.json`
      link.click()
      URL.revokeObjectURL(url)
      toast.success("Download iniciado")
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível baixar seus dados"))
    } finally {
      setPending(false)
    }
  }

  return { pending, download }
}
