import { describe, it, expect, vi, beforeEach } from "vitest"

vi.mock("./client", () => ({ default: { post: vi.fn(), get: vi.fn() } }))

import apiClient from "./client"
import { claimPix, getPeladaSubscription } from "./subscription"

const mockGet = vi.mocked(apiClient.get)
const mockPost = vi.mocked(apiClient.post)

beforeEach(() => {
  mockGet.mockReset()
  mockPost.mockReset()
})

describe("subscription", () => {
  it("reads the creator's view of the pelada's subscription", async () => {
    mockGet.mockResolvedValue({ data: { status: "TRIAL" } })

    await expect(getPeladaSubscription(7)).resolves.toEqual({ status: "TRIAL" })
    expect(mockGet).toHaveBeenCalledWith("/api/v1/peladas/7/subscription")
  })

  it("informs the Pix of a plan", async () => {
    mockPost.mockResolvedValue({ data: { claimedPlan: "ANNUAL" } })

    await expect(claimPix(7, { plan: "ANNUAL" })).resolves.toEqual({ claimedPlan: "ANNUAL" })
    expect(mockPost).toHaveBeenCalledWith("/api/v1/peladas/7/subscription/pix-claim", { plan: "ANNUAL" })
  })
})
