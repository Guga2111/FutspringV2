import { useMemo, useState } from "react"
import { useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { populateFromMessage } from "@/api/dailies"
import { applyServerErrors } from "@/lib/form-errors"
import {
  importMessageSchema,
  importResolutionSchema,
  toPopulateInput,
  type ImportMessageValues,
  type ImportResolutionValues,
} from "@/schemas/daily"
import type { DailyDetail } from "@/types/daily"
import { autoMatchPlayers } from "@/utils/matchPlayers"
import { parseSessionMessage, type ParsedSession } from "@/utils/parseSessionMessage"

// "Importar da mensagem": step 1 is the pasted text (its own form, kept when going back), step 2 links each
// name of the parsed message to a member (a form seeded from the parse). Teams and matches come from the parse
export function useImportFromMessage(daily: DailyDetail, onSuccess: (updated: DailyDetail) => void) {
  const members = useMemo(() => daily.peladaMembers ?? [], [daily.peladaMembers])
  const [step, setStep] = useState<1 | 2>(1)
  const [parsed, setParsed] = useState<ParsedSession | null>(null)

  const messageForm = useForm<ImportMessageValues>({
    resolver: zodResolver(importMessageSchema),
    defaultValues: { message: "" },
  })
  const resolutionForm = useForm<ImportResolutionValues>({
    resolver: zodResolver(importResolutionSchema),
    defaultValues: { teams: [] },
  })

  const analyze = messageForm.handleSubmit(({ message }) => {
    const session = parseSessionMessage(message)
    if (session.teams.length === 0) {
      messageForm.setError("message", { type: "parse", message: "Nenhum time encontrado. Verifique o formato da mensagem." })
      return
    }
    // A name that matches exactly one member comes linked; the others wait for a choice
    const matched = autoMatchPlayers(session, members)
    resolutionForm.reset({
      teams: matched.map((players) => ({
        players: players.map(({ rawName, totalGoals, totalAssists, matchedUserId }) => ({
          rawName,
          totalGoals,
          totalAssists,
          matchedUserId,
          skipped: false,
        })),
      })),
    })
    setParsed(session)
    setStep(2)
  })

  const submit = resolutionForm.handleSubmit(async (values) => {
    if (!parsed) return
    try {
      const updated = await populateFromMessage(daily.id, toPopulateInput(parsed, values))
      toast.success("Sessão importada")
      onSuccess(updated)
    } catch (error) {
      applyServerErrors(resolutionForm, error, "Não foi possível importar a sessão")
    }
  })

  // Members already linked to a name aren't offered again
  const teams = useWatch({ control: resolutionForm.control, name: "teams" })
  const linkedIds = useMemo(
    () => new Set(teams.flatMap((team) => team.players.flatMap((p) => (p.matchedUserId === null ? [] : [p.matchedUserId])))),
    [teams],
  )

  return {
    step,
    back: () => setStep(1),
    parsed,
    members,
    linkedIds,
    messageForm,
    resolutionForm,
    analyze,
    submit,
  }
}
