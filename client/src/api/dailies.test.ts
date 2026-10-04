import { describe, it, expect, vi, beforeEach } from "vitest"

vi.mock("./client", () => ({
  default: { post: vi.fn(), get: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import apiClient from "./client"
import { adminConfirmAllAttendance } from "./dailies"

const mockPost = vi.mocked(apiClient.post)

beforeEach(() => {
  mockPost.mockReset()
})

describe("adminConfirmAllAttendance", () => {
  it("posts to the confirm-all route and returns the list item", async () => {
    const item = { id: 7, confirmedPlayerCount: 10, isConfirmed: true }
    mockPost.mockResolvedValue({ data: item })

    await expect(adminConfirmAllAttendance(7)).resolves.toEqual(item)
    expect(mockPost).toHaveBeenCalledWith("/api/v1/dailies/7/confirm/all")
  })
})
