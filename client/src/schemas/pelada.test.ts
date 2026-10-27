import { describe, it, expect } from "vitest"
import { makeUpdatePeladaSchema } from "./pelada"

const valid = {
  name: "Pelada do Fut",
  dayOfWeek: "FRIDAY",
  timeOfDay: "20:00",
  duration: 1.5,
  address: "",
  reference: "",
  autoCreateDailyEnabled: false,
  timeZone: "America/Sao_Paulo",
  maxMensalistas: 8,
}

describe("makeUpdatePeladaSchema", () => {
  const schema = makeUpdatePeladaSchema(10)

  it("accepts a valid pelada", () => {
    expect(schema.safeParse(valid).success).toBe(true)
  })

  it("caps the mensalista limit at the capacity", () => {
    const result = schema.safeParse({ ...valid, maxMensalistas: 11 })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0].message).toBe("O limite de mensalistas não pode passar das vagas (10)")
    expect(schema.safeParse({ ...valid, maxMensalistas: 10 }).success).toBe(true)
  })

  it("rejects a negative or fractional limit, a bad time and an empty zone", () => {
    expect(schema.safeParse({ ...valid, maxMensalistas: -1 }).success).toBe(false)
    expect(schema.safeParse({ ...valid, maxMensalistas: 1.5 }).success).toBe(false)
    expect(schema.safeParse({ ...valid, timeOfDay: "25:00" }).success).toBe(false)
    expect(schema.safeParse({ ...valid, timeZone: "" }).success).toBe(false)
    expect(schema.safeParse({ ...valid, dayOfWeek: "Sexta" }).success).toBe(false)
  })
})
