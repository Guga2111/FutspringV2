import { describe, it, expect, vi, beforeEach } from "vitest"

vi.mock("./client", () => ({ default: { post: vi.fn(), get: vi.fn() } }))

import apiClient from "./client"
import { getNotifications, getUnreadNotificationCount, markAllNotificationsRead, markNotificationRead } from "./notifications"

const mockGet = vi.mocked(apiClient.get)
const mockPost = vi.mocked(apiClient.post)

beforeEach(() => {
  mockGet.mockReset()
  mockPost.mockReset()
  mockPost.mockResolvedValue({ data: undefined })
})

describe("getNotifications", () => {
  it("sends page, size and the unread filter", async () => {
    mockGet.mockResolvedValue({ data: [] })

    await getNotifications({ page: 2, unread: true })

    expect(mockGet).toHaveBeenCalledWith("/api/v1/notifications", { params: { page: 2, size: 20, unread: true } })
  })

  it("defaults to every notification", async () => {
    mockGet.mockResolvedValue({ data: [] })

    await getNotifications({ page: 0, size: 5 })

    expect(mockGet).toHaveBeenCalledWith("/api/v1/notifications", { params: { page: 0, size: 5, unread: false } })
  })
})

describe("getUnreadNotificationCount", () => {
  it("returns the count", async () => {
    mockGet.mockResolvedValue({ data: { count: 3 } })

    await expect(getUnreadNotificationCount()).resolves.toBe(3)
    expect(mockGet).toHaveBeenCalledWith("/api/v1/notifications/unread-count")
  })
})

describe("read", () => {
  it("marks one notification", async () => {
    await markNotificationRead(7)
    expect(mockPost).toHaveBeenCalledWith("/api/v1/notifications/7/read")
  })

  it("marks every notification", async () => {
    await markAllNotificationsRead()
    expect(mockPost).toHaveBeenCalledWith("/api/v1/notifications/read-all")
  })
})
