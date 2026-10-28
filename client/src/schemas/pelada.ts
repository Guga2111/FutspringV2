import { z } from "zod"
import { DAYS_OF_WEEK } from "@/lib/constants"

const DAY_VALUES = DAYS_OF_WEEK.map((d) => d.value) as [string, ...string[]]

// The settings page has one form per card; each schema mirrors its slice of UpdatePeladaRequestDTO (a partial
// update, so a card only sends its own fields)

// mirrors UpdatePeladaRequestDTO.name
export const peladaInfoSchema = z.object({
  name: z.string().trim().min(1, "O nome é obrigatório").max(100, "Máximo de 100 caracteres"),
})
export type PeladaInfoValues = z.infer<typeof peladaInfoSchema>

// mirrors UpdatePeladaRequestDTO.dayOfWeek, timeOfDay, duration and timeZone
export const peladaScheduleSchema = z.object({
  dayOfWeek: z.enum(DAY_VALUES, { error: "Escolha um dia da semana" }),
  timeOfDay: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/, "Horário inválido (HH:mm)"),
  duration: z.number({ error: "Informe a duração" }).positive("A duração deve ser maior que zero"),
  timeZone: z.string().min(1, "Escolha um fuso horário").max(64, "Máximo de 64 caracteres"),
})
export type PeladaScheduleValues = z.infer<typeof peladaScheduleSchema>

// mirrors UpdatePeladaRequestDTO.address and reference
export const peladaLocationSchema = z.object({
  address: z.string().max(255, "Máximo de 255 caracteres"),
  reference: z.string().max(255, "Máximo de 255 caracteres"),
})
export type PeladaLocationValues = z.infer<typeof peladaLocationSchema>

// mirrors UpdatePeladaRequestDTO.numberOfTeams, playersPerTeam and maxMensalistas. The limit is checked against
// the capacity being edited, as the backend does (409); going below the current mensalistas is only checked there.
export const peladaSlotsSchema = z
  .object({
    numberOfTeams: z
      .number({ error: "Informe o número de times" })
      .int("Use um número inteiro")
      .min(2, "Mínimo de 2 times")
      .max(10, "Máximo de 10 times"),
    playersPerTeam: z
      .number({ error: "Informe os jogadores por time" })
      .int("Use um número inteiro")
      .min(2, "Mínimo de 2 jogadores por time")
      .max(20, "Máximo de 20 jogadores por time"),
    maxMensalistas: z
      .number({ error: "Informe o limite de mensalistas" })
      .int("Use um número inteiro")
      .min(0, "O limite de mensalistas não pode ser negativo"),
  })
  .superRefine((values, ctx) => {
    const capacity = values.numberOfTeams * values.playersPerTeam
    if (values.maxMensalistas > capacity) {
      ctx.addIssue({
        code: "custom",
        path: ["maxMensalistas"],
        message: `O limite de mensalistas não pode passar das vagas (${capacity})`,
      })
    }
  })
export type PeladaSlotsValues = z.infer<typeof peladaSlotsSchema>

// Client only: destructive actions ask for the pelada's name, typed as it is shown (surrounding spaces ignored)
function typedName(peladaName: string) {
  return z.string().refine((value) => value.trim() === peladaName.trim(), "Digite o nome da pelada exatamente como aparece")
}

export function makeDeletePeladaSchema(peladaName: string) {
  return z.object({ confirmation: typedName(peladaName) })
}
export type DeletePeladaValues = z.infer<ReturnType<typeof makeDeletePeladaSchema>>

// mirrors TransferCreatorRequestDTO (userId as the Select's string value) + the typed name
export function makeTransferPeladaSchema(peladaName: string) {
  return z.object({
    userId: z.string().min(1, "Escolha o novo criador"),
    confirmation: typedName(peladaName),
  })
}
export type TransferPeladaValues = z.infer<ReturnType<typeof makeTransferPeladaSchema>>

// mirrors UpdatePlayerStarsRequestDTO and the stars of BulkSetStarsRequestDTO
export const playerStarsSchema = z.object({
  stars: z.number().int().min(1, "Mínimo de 1 estrela").max(5, "Máximo de 5 estrelas"),
})
export type PlayerStarsValues = z.infer<typeof playerStarsSchema>
