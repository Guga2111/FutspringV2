import { z } from "zod"
import { DAYS_OF_WEEK } from "@/lib/constants"

const DAY_VALUES = DAYS_OF_WEEK.map((d) => d.value) as [string, ...string[]]

// mirrors UpdatePeladaRequestDTO. The capacity (numberOfTeams × playersPerTeam) caps maxMensalistas, as the
// backend does with a 409; going below the current number of mensalistas is only checked by the backend.
export function makeUpdatePeladaSchema(capacity: number) {
  return z.object({
    name: z.string().trim().min(1, "O nome é obrigatório").max(100, "Máximo de 100 caracteres"),
    dayOfWeek: z.enum(DAY_VALUES, { error: "Escolha um dia da semana" }),
    timeOfDay: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/, "Horário inválido (HH:mm)"),
    duration: z.number({ error: "Informe a duração" }).positive("A duração deve ser maior que zero"),
    address: z.string().max(255, "Máximo de 255 caracteres"),
    reference: z.string().max(255, "Máximo de 255 caracteres"),
    autoCreateDailyEnabled: z.boolean(),
    timeZone: z.string().min(1, "Escolha um fuso horário").max(64, "Máximo de 64 caracteres"),
    maxMensalistas: z
      .number({ error: "Informe o limite de mensalistas" })
      .int("Use um número inteiro")
      .min(0, "O limite de mensalistas não pode ser negativo")
      .max(capacity, `O limite de mensalistas não pode passar das vagas (${capacity})`),
  })
}
export type UpdatePeladaValues = z.infer<ReturnType<typeof makeUpdatePeladaSchema>>

// mirrors UpdatePlayerStarsRequestDTO
export const playerStarsSchema = z.object({
  stars: z.number().int().min(1, "Mínimo de 1 estrela").max(5, "Máximo de 5 estrelas"),
})
export type PlayerStarsValues = z.infer<typeof playerStarsSchema>
