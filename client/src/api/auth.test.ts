import { beforeEach, describe, expect, it, vi } from "vitest"

vi.mock("./client", () => ({ default: { post: vi.fn() } }))

import apiClient from "./client"
import { requestPasswordReset, resetPassword } from "./auth"

const mockPost = vi.mocked(apiClient.post)

beforeEach(() => {
  mockPost.mockReset()
  mockPost.mockResolvedValue({ data: "" })
})

describe("requestPasswordReset", () => {
  it("posts the e-mail to forgot-password", async () => {
    await requestPasswordReset("ana@example.com")

    expect(mockPost).toHaveBeenCalledWith("/api/v1/auth/forgot-password", { email: "ana@example.com" })
  })
})

describe("resetPassword", () => {
  it("posts the token and the new password to reset-password", async () => {
    await resetPassword({ token: "abc", newPassword: "novasenha1" })

    expect(mockPost).toHaveBeenCalledWith("/api/v1/auth/reset-password", { token: "abc", newPassword: "novasenha1" })
  })
})
