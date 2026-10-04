import { describe, expect, it, vi } from "vitest"
import type { UseFormReturn } from "react-hook-form"
import { applyServerErrors } from "./form-errors"

type Values = { username: string; stars: number }

function fakeForm() {
  const setError = vi.fn()
  const form = { getValues: () => ({ username: "ana", stars: 3 }), setError } as unknown as UseFormReturn<Values>
  return { form, setError }
}

describe("applyServerErrors", () => {
  it("puts field errors on matching fields", () => {
    const { form, setError } = fakeForm()
    const error = { response: { data: { message: "Dados inválidos", errors: { username: "Mínimo de 3 caracteres" } } } }

    applyServerErrors(form, error, "fallback")

    expect(setError).toHaveBeenCalledWith("username", { type: "server", message: "Mínimo de 3 caracteres" })
    expect(setError).toHaveBeenCalledTimes(1)
  })

  it("puts other errors on root", () => {
    const { form, setError } = fakeForm()

    applyServerErrors(form, { response: { data: { message: "Este nome de usuário já está em uso" } } }, "fallback")

    expect(setError).toHaveBeenCalledWith("root", { type: "server", message: "Este nome de usuário já está em uso" })
  })

  it("uses root for field errors of fields the form does not have", () => {
    const { form, setError } = fakeForm()

    applyServerErrors(form, { response: { data: { errors: { other: "x" } } } }, "fallback")

    expect(setError).toHaveBeenCalledWith("root", { type: "server", message: "x" })
  })
})
