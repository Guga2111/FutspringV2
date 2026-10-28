import { describe, it, expect, vi, beforeEach } from "vitest"

vi.mock("./client", () => ({ default: { post: vi.fn(), get: vi.fn(), put: vi.fn() } }))

import apiClient from "./client"
import {
  getNotificationPreferences,
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  resubscribeToEmails,
  unsubscribeFromEmails,
  updateNotificationPreferences,
} from "./notifications"

const mockGet = vi.mocked(apiClient.get)
const mockPost = vi.mocked(apiClient.post)
const mockPut = vi.mocked(apiClient.put)

beforeEach(() => {
  mockGet.mockReset()
  mockPost.mockReset()
  mockPut.mockReset()
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

describe("notification preferences", () => {
  const all = [
    { category: "ATTENDANCE", emailEnabled: false },
    { category: "SESSION", emailEnabled: false },
    { category: "MEMBERSHIP", emailEnabled: true },
    { category: "AWARDS", emailEnabled: false },
  ]

  it("gets the four categories", async () => {
    mockGet.mockResolvedValue({ data: all })

    await expect(getNotificationPreferences()).resolves.toEqual(all)
    expect(mockGet).toHaveBeenCalledWith("/api/v1/notifications/preferences")
  })

  it("sends only the changed category", async () => {
    mockPut.mockResolvedValue({ data: all })

    await expect(updateNotificationPreferences([{ category: "ATTENDANCE", emailEnabled: false }])).resolves.toEqual(all)
    expect(mockPut).toHaveBeenCalledWith("/api/v1/notifications/preferences", [
      { category: "ATTENDANCE", emailEnabled: false },
    ])
  })
})

describe("unsubscribe links", () => {
  it("posts the token to unsubscribe", async () => {
    mockPost.mockResolvedValue({ data: { category: "ATTENDANCE", emailEnabled: false } })

    await expect(unsubscribeFromEmails("a.b")).resolves.toEqual({ category: "ATTENDANCE", emailEnabled: false })
    expect(mockPost).toHaveBeenCalledWith("/api/v1/notifications/unsubscribe", { token: "a.b" })
  })

  it("posts the token to resubscribe", async () => {
    mockPost.mockResolvedValue({ data: { category: "ATTENDANCE", emailEnabled: true } })

    await expect(resubscribeToEmails("a.b")).resolves.toEqual({ category: "ATTENDANCE", emailEnabled: true })
    expect(mockPost).toHaveBeenCalledWith("/api/v1/notifications/resubscribe", { token: "a.b" })
  })
})
