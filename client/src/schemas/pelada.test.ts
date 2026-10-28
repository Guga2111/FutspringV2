import { describe, it, expect } from "vitest"
import {
  makeDeletePeladaSchema,
  makeTransferPeladaSchema,
  peladaInfoSchema,
  peladaLocationSchema,
  peladaScheduleSchema,
  peladaSlotsSchema,
} from "./pelada"

describe("peladaInfoSchema", () => {
  it("trims the name and rejects an empty one", () => {
    expect(peladaInfoSchema.parse({ name: "  Pelada do Fut " }).name).toBe("Pelada do Fut")
    expect(peladaInfoSchema.safeParse({ name: "   " }).success).toBe(false)
  })
})

describe("peladaScheduleSchema", () => {
  const valid = { dayOfWeek: "FRIDAY", timeOfDay: "20:00", duration: 1.5, timeZone: "America/Sao_Paulo" }

  it("accepts a valid schedule", () => {
    expect(peladaScheduleSchema.safeParse(valid).success).toBe(true)
  })

  it("rejects a bad time, an empty zone, a Portuguese day and a zero duration", () => {
    expect(peladaScheduleSchema.safeParse({ ...valid, timeOfDay: "25:00" }).success).toBe(false)
    expect(peladaScheduleSchema.safeParse({ ...valid, timeZone: "" }).success).toBe(false)
    expect(peladaScheduleSchema.safeParse({ ...valid, dayOfWeek: "Sexta" }).success).toBe(false)
    expect(peladaScheduleSchema.safeParse({ ...valid, duration: 0 }).success).toBe(false)
  })
})

describe("peladaLocationSchema", () => {
  it("accepts empty fields and caps the length", () => {
    expect(peladaLocationSchema.safeParse({ address: "", reference: "" }).success).toBe(true)
    expect(peladaLocationSchema.safeParse({ address: "a".repeat(256), reference: "" }).success).toBe(false)
  })
})

describe("peladaSlotsSchema", () => {
  const valid = { numberOfTeams: 2, playersPerTeam: 5, maxMensalistas: 8 }

  it("accepts a valid set of slots", () => {
    expect(peladaSlotsSchema.safeParse(valid).success).toBe(true)
  })

  it("checks the limit against the capacity being edited", () => {
    const result = peladaSlotsSchema.safeParse({ ...valid, maxMensalistas: 11 })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0].path).toEqual(["maxMensalistas"])
    expect(result.error?.issues[0].message).toBe("O limite de mensalistas não pode passar das vagas (10)")
    // 3 teams × 5 = 15 spots: the same limit now fits
    expect(peladaSlotsSchema.safeParse({ ...valid, numberOfTeams: 3, maxMensalistas: 11 }).success).toBe(true)
  })

  it("mirrors the DTO's ranges", () => {
    expect(peladaSlotsSchema.safeParse({ ...valid, numberOfTeams: 1 }).success).toBe(false)
    expect(peladaSlotsSchema.safeParse({ ...valid, numberOfTeams: 11 }).success).toBe(false)
    expect(peladaSlotsSchema.safeParse({ ...valid, playersPerTeam: 21 }).success).toBe(false)
    expect(peladaSlotsSchema.safeParse({ ...valid, maxMensalistas: -1 }).success).toBe(false)
    expect(peladaSlotsSchema.safeParse({ ...valid, maxMensalistas: 1.5 }).success).toBe(false)
    expect(peladaSlotsSchema.safeParse({ ...valid, numberOfTeams: Number.NaN }).success).toBe(false)
  })
})

describe("typed-name confirmations", () => {
  it("only accepts the pelada's name as shown", () => {
    const schema = makeDeletePeladaSchema("Pelada do Fut")
    expect(schema.safeParse({ confirmation: " Pelada do Fut " }).success).toBe(true)
    expect(schema.safeParse({ confirmation: "pelada do fut" }).success).toBe(false)
    expect(schema.safeParse({ confirmation: "" }).success).toBe(false)
  })

  it("asks for the new creator when transferring", () => {
    const schema = makeTransferPeladaSchema("Pelada do Fut")
    expect(schema.safeParse({ userId: "", confirmation: "Pelada do Fut" }).success).toBe(false)
    expect(schema.safeParse({ userId: "7", confirmation: "Pelada do Fut" }).success).toBe(true)
  })
})
