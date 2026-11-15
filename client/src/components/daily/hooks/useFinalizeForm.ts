import { useMemo } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { finalizeDaily } from "@/api/dailies"
import { applyServerErrors } from "@/lib/form-errors"
import { finalizeDailySchema, type FinalizeDailyValues } from "@/schemas/daily"
import type { DailyDetail } from "@/types/daily"

// The finalize dialog's form: the Puskás and Bola Murcha winners, then the server computes stats and ranking
export function useFinalizeForm(daily: DailyDetail, onSuccess: (updated: DailyDetail) => void) {
  const form = useForm<FinalizeDailyValues>({
    resolver: zodResolver(finalizeDailySchema),
    defaultValues: { puskasWinnerIds: [], wiltballWinnerIds: [] },
  })
  // Awards go to players on the teams (the backend's session players), not the confirmed list
  const sessionPlayers = useMemo(() => daily.teams.flatMap((t) => t.players), [daily.teams])

  const submit = form.handleSubmit(async (values) => {
    try {
      const updated = await finalizeDaily(daily.id, values.puskasWinnerIds, values.wiltballWinnerIds)
      toast.success("Sessão finalizada")
      onSuccess(updated)
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível finalizar a sessão")
    }
  })

  return { form, sessionPlayers, submit }
}
