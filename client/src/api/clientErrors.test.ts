import { describe, it, expect, vi } from "vitest"

vi.mock("./client", () => ({ default: { post: vi.fn() } }))

import apiClient from "./client"
import { sendClientError, type ClientErrorReport } from "./clientErrors"

describe("clientErrors", () => {
  it("posts the report", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: undefined })
    const report: ClientErrorReport = {
      message: "boom",
      stack: null,
      path: "/home",
      release: "test",
      userAgent: "UA",
      source: "WINDOW",
    }

    await sendClientError(report)

    expect(apiClient.post).toHaveBeenCalledWith("/api/v1/client-errors", report)
  })
})
