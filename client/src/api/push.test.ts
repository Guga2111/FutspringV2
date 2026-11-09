import { beforeEach, describe, expect, it, vi } from "vitest"

vi.mock("./client", () => ({ default: { post: vi.fn(), get: vi.fn() } }))

import apiClient from "./client"
import { getVapidPublicKey, subscribePush, unsubscribePush } from "./push"

const mockGet = vi.mocked(apiClient.get)
const mockPost = vi.mocked(apiClient.post)

beforeEach(() => {
  mockGet.mockReset()
  mockPost.mockReset()
  mockPost.mockResolvedValue({ data: undefined })
})

describe("getVapidPublicKey", () => {
  it("returns the key", async () => {
    mockGet.mockResolvedValue({ status: 200, data: { publicKey: "BPk" } })
    await expect(getVapidPublicKey()).resolves.toBe("BPk")
    expect(mockGet).toHaveBeenCalledWith("/api/v1/push/vapid-public-key")
  })

  it("is null when push isn't configured (204)", async () => {
    mockGet.mockResolvedValue({ status: 204, data: "" })
    await expect(getVapidPublicKey()).resolves.toBeNull()
  })
})

describe("subscribePush", () => {
  it("posts the subscription", async () => {
    const body = { endpoint: "https://fcm.googleapis.com/fcm/send/a", keys: { p256dh: "p", auth: "a" } }
    await subscribePush(body)
    expect(mockPost).toHaveBeenCalledWith("/api/v1/push/subscriptions", body)
  })
})

describe("unsubscribePush", () => {
  it("posts the endpoint with the caller's session", async () => {
    await unsubscribePush("https://e")
    expect(mockPost).toHaveBeenCalledWith("/api/v1/push/subscriptions/remove", { endpoint: "https://e" }, { timeout: undefined, headers: undefined })
  })

  it("can send a closing session's token with a timeout", async () => {
    await unsubscribePush("https://e", { token: "t", timeout: 2000 })
    expect(mockPost).toHaveBeenCalledWith(
      "/api/v1/push/subscriptions/remove",
      { endpoint: "https://e" },
      { timeout: 2000, headers: { Authorization: "Bearer t" } },
    )
  })
})
