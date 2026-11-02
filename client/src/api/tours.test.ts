import { describe, it, expect, vi, beforeEach } from "vitest"

vi.mock("./client", () => ({ default: { post: vi.fn(), get: vi.fn() } }))

import apiClient from "./client"
import { getTourState, markTourSeen } from "./tours"

const mockGet = vi.mocked(apiClient.get)
const mockPost = vi.mocked(apiClient.post)

beforeEach(() => {
  mockGet.mockReset()
  mockPost.mockReset()
})

describe("tours", () => {
  it("reads the caller's seen tours", async () => {
    mockGet.mockResolvedValue({ data: { seen: ["FINANCE_MEMBER"] } })

    await expect(getTourState()).resolves.toEqual({ seen: ["FINANCE_MEMBER"] })
    expect(mockGet).toHaveBeenCalledWith("/api/v1/tours")
  })

  it("marks one tour as seen", async () => {
    mockPost.mockResolvedValue({ data: undefined })

    await markTourSeen("FINANCE_MANAGER")

    expect(mockPost).toHaveBeenCalledWith("/api/v1/tours/FINANCE_MANAGER/seen")
  })
})
