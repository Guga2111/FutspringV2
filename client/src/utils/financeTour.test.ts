import { describe, it, expect, vi } from "vitest"
import { financeTourKey, financeTourSteps } from "./financeTour"

const ids = (manage: boolean, viewCash: boolean) => financeTourSteps(manage, viewCash).map((step) => step.id)

describe("financeTourSteps", () => {
  it("walks a manager through creating, receiving, cash and settings", () => {
    expect(ids(true, true)).toEqual(["welcome", "new-collection", "scope", "collections", "cash", "settings", "help"])
  })

  it("shows a member only their charges, and the cash in transparent mode", () => {
    expect(ids(false, false)).toEqual(["welcome", "collections", "scope", "help"])
    expect(ids(false, true)).toEqual(["welcome", "collections", "scope", "cash", "help"])
  })

  it("starts with skip / begin, ends with conclude", () => {
    const steps = financeTourSteps(false, false)
    expect(steps[0].type).toBe("dialog")
    expect(steps[0].actions?.map((a) => a.action)).toEqual(["skip", "next"])
    expect(steps[1].type).toBe("tooltip")
    expect(steps.at(-1)?.actions?.map((a) => a.label)).toEqual(["Voltar", "Concluir"])
  })

  it("resolves each target from its data-tour attribute", () => {
    const element = {}
    const querySelector = vi.fn(() => element)
    vi.stubGlobal("document", { querySelector })
    const help = financeTourSteps(true, true).find((step) => step.id === "help")
    expect(help?.target?.()).toBe(element)
    expect(querySelector).toHaveBeenCalledWith('[data-tour="help"]')
    vi.unstubAllGlobals()
  })
})

describe("financeTourKey", () => {
  it("has one tour per role", () => {
    expect(financeTourKey(true)).toBe("FINANCE_MANAGER")
    expect(financeTourKey(false)).toBe("FINANCE_MEMBER")
  })
})
