import { useMemo } from "react"
import { useFieldArray, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { getDailyDetail, submitResults } from "@/api/dailies"
import { applyServerErrors } from "@/lib/form-errors"
import { makeResultsSchema, toMatchResultInputs, type MatchRowValues, type ResultsValues } from "@/schemas/daily"
import { buildPlayerStats } from "@/utils/matchStats"
import { suggestPairing } from "@/utils/liveSession"
import type { DailyDetail } from "@/types/daily"

// add: new matches appended to the saved ones (live session); edit: every saved match (finished session)
export type ResultsMode = "add" | "edit"

function savedRows(daily: DailyDetail): MatchRowValues[] {
  return daily.matches.map((m) => ({
    matchId: m.id,
    team1Id: m.team1Id,
    team2Id: m.team2Id,
    team1Score: m.team1Score ?? 0,
    team2Score: m.team2Score ?? 0,
    playerStats: buildPlayerStats(daily, m.team1Id, m.team2Id, m.playerStats),
  }))
}

export function newMatchRow(daily: DailyDetail, matchIndex: number): MatchRowValues {
  const [i1, i2] = suggestPairing(daily.teams.length, matchIndex)
  const team1Id = daily.teams[i1]?.id ?? 0
  const team2Id = daily.teams[i2]?.id ?? 0
  return {
    matchId: null,
    team1Id,
    team2Id,
    team1Score: 0,
    team2Score: 0,
    playerStats: buildPlayerStats(daily, team1Id, team2Id),
  }
}

// The results dialog's form. The API treats the submitted list as the session's full set of matches, so in
// "add" mode the saved matches are sent too, unchanged.
export function useResultsForm(daily: DailyDetail, mode: ResultsMode, onSaved: (updated: DailyDetail) => void) {
  const schema = useMemo(() => makeResultsSchema(daily.teams), [daily.teams])
  const form = useForm<ResultsValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      matches: mode === "edit" && daily.matches.length > 0 ? savedRows(daily) : [newMatchRow(daily, daily.matches.length)],
    },
  })
  const fieldArray = useFieldArray({ control: form.control, name: "matches" })
  // Numbering continues after the saved matches when adding
  const firstNumber = mode === "add" ? daily.matches.length + 1 : 1

  function addMatch() {
    fieldArray.append(newMatchRow(daily, firstNumber - 1 + fieldArray.fields.length))
  }

  async function onSubmit(values: ResultsValues) {
    const rows = mode === "add" ? [...savedRows(daily), ...values.matches] : values.matches
    try {
      await submitResults(daily.id, toMatchResultInputs(rows))
      // the league table and stats are recomputed server-side: reload the detail
      const updated = await getDailyDetail(daily.id)
      toast.success("Resultados salvos")
      onSaved(updated)
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível salvar os resultados")
    }
  }

  return { form, fieldArray, firstNumber, addMatch, submit: form.handleSubmit(onSubmit) }
}
