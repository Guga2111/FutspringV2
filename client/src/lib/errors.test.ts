import { describe, expect, it } from "vitest"
import { getApiErrorBody, getErrorMessage, getErrorStatus } from "./errors"

const axiosLike = (status: number, data: unknown) => ({ response: { status, data } })

describe("getErrorMessage", () => {
  it("returns the API message", () => {
    expect(getErrorMessage(axiosLike(403, { status: 403, message: "Acesso negado" }), "fallback")).toBe("Acesso negado")
  })

  it("prefers the first field error of a validation 400", () => {
    const error = axiosLike(400, { status: 400, message: "Dados inválidos", errors: { name: "O nome é obrigatório" } })
    expect(getErrorMessage(error, "fallback")).toBe("O nome é obrigatório")
  })

  it("falls back for network errors and unknown values", () => {
    expect(getErrorMessage(new Error("Network Error"), "Sem conexão")).toBe("Sem conexão")
    expect(getErrorMessage(undefined, "Sem conexão")).toBe("Sem conexão")
    expect(getErrorMessage(axiosLike(500, "<html>"), "Erro")).toBe("Erro")
  })
})

describe("getErrorStatus / getApiErrorBody", () => {
  it("reads the HTTP status and body", () => {
    const error = axiosLike(404, { message: "Pelada não encontrada" })
    expect(getErrorStatus(error)).toBe(404)
    expect(getApiErrorBody(error)?.message).toBe("Pelada não encontrada")
  })

  it("returns nothing for non-HTTP errors", () => {
    expect(getErrorStatus("boom")).toBeUndefined()
    expect(getApiErrorBody(null)).toBeNull()
  })
})
