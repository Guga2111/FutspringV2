import { describe, it, expect, vi, beforeEach } from "vitest"

vi.mock("./client", () => ({ default: { post: vi.fn(), get: vi.fn() } }))

import apiClient from "./client"
import {
  changePlatformSubscription,
  getPlatformOverview,
  getPlatformPayments,
  getPlatformPelada,
  getPlatformPeladas,
  getPlatformUsers,
  recordPlatformPayment,
  voidPlatformPayment,
} from "./platform"

const mockGet = vi.mocked(apiClient.get)
const mockPost = vi.mocked(apiClient.post)

beforeEach(() => {
  mockGet.mockReset().mockResolvedValue({ data: {} })
  mockPost.mockReset().mockResolvedValue({ data: {} })
})

describe("platform", () => {
  it("reads the overview and one pelada", async () => {
    await getPlatformOverview()
    await getPlatformPelada(7)
    expect(mockGet).toHaveBeenNthCalledWith(1, "/api/v1/platform/overview")
    expect(mockGet).toHaveBeenNthCalledWith(2, "/api/v1/platform/peladas/7")
  })

  it("sends only the filters that are set", async () => {
    await getPlatformPeladas(0)
    await getPlatformPeladas(2, { state: "PAST_DUE", q: "  fut " })
    await getPlatformPayments(1, { from: "2026-10-01", to: "2026-10-31" })
    await getPlatformUsers(0, "  ")
    expect(mockGet.mock.calls[0]).toEqual(["/api/v1/platform/peladas", { params: { page: 0, size: 20 } }])
    expect(mockGet.mock.calls[1]).toEqual(["/api/v1/platform/peladas", { params: { state: "PAST_DUE", q: "fut", page: 2, size: 20 } }])
    expect(mockGet.mock.calls[2]).toEqual(["/api/v1/platform/payments", { params: { from: "2026-10-01", to: "2026-10-31", page: 1, size: 20 } }])
    expect(mockGet.mock.calls[3]).toEqual(["/api/v1/platform/users", { params: { page: 0, size: 20 } }])
  })

  it("posts the commands", async () => {
    await recordPlatformPayment(7, { plan: "ANNUAL", amount: "199.00", paidOn: "2026-11-04", note: null })
    await changePlatformSubscription(7, { action: "EXTEND_DAYS", days: 15, note: "Cortesia" })
    await voidPlatformPayment(3, "Pix devolvido")
    expect(mockPost.mock.calls).toEqual([
      ["/api/v1/platform/peladas/7/payments", { plan: "ANNUAL", amount: "199.00", paidOn: "2026-11-04", note: null }],
      ["/api/v1/platform/peladas/7/subscription", { action: "EXTEND_DAYS", days: 15, note: "Cortesia" }],
      ["/api/v1/platform/payments/3/void", { reason: "Pix devolvido" }],
    ])
  })
})
