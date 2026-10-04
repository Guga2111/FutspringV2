import { z } from "zod"

// mirrors CreateDailyRequestDTO (dailyDate is sent as yyyy-MM-dd)
export const createDailySchema = z.object({
  dailyDate: z
    .date()
    .nullable()
    .refine((date): date is Date => date !== null, "Escolha uma data"),
  dailyTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, "Horário inválido (HH:mm)"),
})
export type CreateDailyInput = z.input<typeof createDailySchema>
export type CreateDailyValues = z.output<typeof createDailySchema>
