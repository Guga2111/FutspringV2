import { useEffect, useMemo } from "react"
import { useFieldArray, useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { saveTeamsSchema, type SaveTeamsValues } from "@/schemas/daily"
import { applyServerErrors } from "@/lib/form-errors"
import { getErrorStatus } from "@/lib/errors"
import {
  addTeam,
  draftFromDaily,
  MAX_TEAMS,
  MIN_TEAMS,
  moveToTeam,
  reconcileDraft,
  teamPendencies,
  unassignedPlayers,
} from "@/utils/teamDraft"
import type { DailyDetail, PlayerDTO } from "@/types/daily"

interface Options {
  daily: DailyDetail
  onSave: (values: SaveTeamsValues) => Promise<void>
  refetch: () => Promise<void>
  onSaved: () => void
}

// State of the "Montar times" editor: a react-hook-form draft of every team, the moves between teams and
// "Sem time", and the save. A 409 (attendance changed meanwhile) reloads the session and the draft follows it.
export function useTeamsEditor({ daily, onSave, refetch, onSaved }: Options) {
  const form = useForm<SaveTeamsValues>({
    resolver: zodResolver(saveTeamsSchema),
    defaultValues: { teams: draftFromDaily(daily) },
  })
  const fields = useFieldArray({ control: form.control, name: "teams" })
  const teams = useWatch({ control: form.control, name: "teams" })

  // Whoever is no longer confirmed leaves the draft (on open, and after a reload); new players land in "Sem time"
  useEffect(() => {
    const current = form.getValues("teams")
    reconcileDraft(current, daily.confirmedPlayers).forEach((team, index) => {
      if (team.playerIds.length !== current[index].playerIds.length) {
        form.setValue(`teams.${index}.playerIds`, team.playerIds, { shouldDirty: true })
      }
    })
  }, [daily.confirmedPlayers, form])

  const playersById = useMemo(() => {
    const map = new Map<number, PlayerDTO>()
    for (const player of daily.confirmedPlayers) map.set(player.id, player)
    return map
  }, [daily.confirmedPlayers])

  const unassigned = unassignedPlayers(teams, daily.confirmedPlayers)
  const pendencies = teamPendencies(teams, daily.confirmedPlayers, daily.playersPerTeam)

  // null = "Sem time"; only the teams that change are written, so the field array keeps its ids
  function move(playerId: number, target: number | null) {
    const current = form.getValues("teams")
    const next = moveToTeam(current, playerId, target)
    next.forEach((team, index) => {
      if (team.playerIds.join() !== current[index].playerIds.join()) {
        form.setValue(`teams.${index}.playerIds`, team.playerIds, {
          shouldDirty: true,
          shouldValidate: form.formState.isSubmitted,
        })
      }
    })
    form.clearErrors("root")
  }

  function appendTeam() {
    const next = addTeam(form.getValues("teams"))
    if (next.length > fields.fields.length) fields.append(next[next.length - 1])
  }

  function removeTeamAt(index: number) {
    if (fields.fields.length > MIN_TEAMS) fields.remove(index)
  }

  async function submit(values: SaveTeamsValues) {
    try {
      await onSave(values)
      onSaved()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível salvar os times")
      // the attendance changed while editing: reload it, the effect above reconciles the draft
      if (getErrorStatus(error) === 409) await refetch()
    }
  }

  return {
    form,
    fields: fields.fields,
    teams,
    playersById,
    unassigned,
    pendencies,
    move,
    appendTeam,
    removeTeamAt,
    canAddTeam: fields.fields.length < MAX_TEAMS,
    canRemoveTeam: fields.fields.length > MIN_TEAMS,
    onSubmit: form.handleSubmit(submit),
  }
}
