import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

vi.mock("@/api/auth", () => ({ refreshSession: vi.fn() }))

import { refreshSession } from "@/api/auth"
import {
  freshAccessToken,
  getAccessToken,
  installSessionInterceptors,
  onSessionExpired,
  onSessionRefreshed,
  refreshAccessToken,
  setAccessToken,
  tokenExpiry,
} from "./session"

const mockRefresh = vi.mocked(refreshSession)
const user = { id: 1, email: "leal@futspring.com", username: "Leal", image: null, backgroundImage: null, position: null }

function jwt(exp: number) {
  const payload = btoa(JSON.stringify({ sub: "leal@futspring.com", exp })).replace(/=+$/, "")
  return `h.${payload}.s`
}

function respond(config: InternalAxiosRequestConfig, status: number): AxiosResponse {
  const response = { data: {}, status, statusText: "", headers: {}, config }
  if (status >= 400) throw new AxiosError("falhou", "ERR_BAD_REQUEST", config, null, response)
  return response
}

function statusError(status: number) {
  return new AxiosError("falhou", "ERR_BAD_REQUEST", undefined, null, { data: {}, status, statusText: "", headers: {}, config: {} as InternalAxiosRequestConfig })
}

// An API that accepts only the token "novo"; records the Authorization of every request
function client() {
  const seen: (string | undefined)[] = []
  const instance = axios.create({
    adapter: async (config) => {
      const auth = config.headers.get("Authorization") as string | undefined
      seen.push(auth)
      return respond(config, auth === "Bearer novo" ? 200 : 401)
    },
  })
  installSessionInterceptors(instance)
  return { instance, seen }
}

let expired: ReturnType<typeof vi.fn>
let stops: (() => void)[] = []

beforeEach(() => {
  mockRefresh.mockReset()
  setAccessToken("velho")
  expired = vi.fn()
  stops = [onSessionExpired(expired)]
})

afterEach(() => {
  stops.forEach((stop) => stop())
})

describe("refreshAccessToken", () => {
  it("shares one refresh between concurrent callers and keeps the new token", async () => {
    mockRefresh.mockResolvedValue({ token: "novo", user })
    const refreshed = vi.fn()
    stops.push(onSessionRefreshed(refreshed))

    const [a, b] = await Promise.all([refreshAccessToken(), refreshAccessToken()])

    expect(mockRefresh).toHaveBeenCalledTimes(1)
    expect(a).toBe(b)
    expect(getAccessToken()).toBe("novo")
    expect(refreshed).toHaveBeenCalledTimes(1)
  })

  it("doesn't bring back a session ended while it was in flight", async () => {
    let answer: (value: { token: string; user: typeof user }) => void = () => undefined
    mockRefresh.mockReturnValue(new Promise((resolve) => { answer = resolve }))
    const pending = refreshAccessToken()
    setAccessToken(null)
    answer({ token: "novo", user })
    await pending
    expect(getAccessToken()).toBeNull()
  })
})

describe("installSessionInterceptors", () => {
  it("sends the current token, unless the call set its own", async () => {
    setAccessToken("novo")
    const { instance, seen } = client()
    await instance.get("/api/v1/peladas/my")
    await instance.post("/api/v1/push/subscriptions/remove", {}, { headers: { Authorization: "Bearer fechando" } }).catch(() => undefined)
    expect(seen).toEqual(["Bearer novo", "Bearer fechando"])
  })

  it("two simultaneous 401s make one refresh and both requests are repeated with the new token", async () => {
    mockRefresh.mockResolvedValue({ token: "novo", user })
    const { instance, seen } = client()

    const results = await Promise.all([instance.get("/api/v1/peladas/my"), instance.get("/api/v1/notifications/unread-count")])

    expect(results.map((r) => r.status)).toEqual([200, 200])
    expect(mockRefresh).toHaveBeenCalledTimes(1)
    expect(seen).toEqual(["Bearer velho", "Bearer velho", "Bearer novo", "Bearer novo"])
    expect(expired).not.toHaveBeenCalled()
  })

  it("ends the session when the refresh answers 401", async () => {
    mockRefresh.mockRejectedValue(statusError(401))
    const { instance } = client()
    await expect(instance.get("/api/v1/peladas/my")).rejects.toMatchObject({ response: { status: 401 } })
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it("keeps the session when the refresh can't answer (503, offline)", async () => {
    mockRefresh.mockRejectedValue(statusError(503))
    const { instance } = client()
    await expect(instance.get("/api/v1/peladas/my")).rejects.toMatchObject({ response: { status: 401 } })
    expect(expired).not.toHaveBeenCalled()
  })

  it("ends the session when the repeated request gets 401 again", async () => {
    mockRefresh.mockResolvedValue({ token: "outro", user })
    const { instance, seen } = client()
    await expect(instance.get("/api/v1/peladas/my")).rejects.toMatchObject({ response: { status: 401 } })
    expect(mockRefresh).toHaveBeenCalledTimes(1)
    expect(seen).toHaveLength(2)
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it("leaves 401s of /auth/* and of the push removal to the caller", async () => {
    const { instance } = client()
    await expect(instance.post("/api/v1/auth/login", {})).rejects.toBeInstanceOf(AxiosError)
    await expect(instance.post("/api/v1/push/subscriptions/remove", {})).rejects.toBeInstanceOf(AxiosError)
    expect(mockRefresh).not.toHaveBeenCalled()
    expect(expired).not.toHaveBeenCalled()
  })
})

describe("freshAccessToken", () => {
  const now = Date.UTC(2026, 10, 4, 18)

  it("reads the expiry of a JWT", () => {
    expect(tokenExpiry(jwt(1_800_000_000))).toBe(1_800_000_000)
    expect(tokenExpiry("não-é-jwt")).toBeNull()
  })

  it("keeps a token far from its expiry", async () => {
    const token = jwt(now / 1000 + 600)
    setAccessToken(token)
    await expect(freshAccessToken(30_000, now)).resolves.toBe(token)
    expect(mockRefresh).not.toHaveBeenCalled()
  })

  it("refreshes a token about to expire", async () => {
    setAccessToken(jwt(now / 1000 + 10))
    mockRefresh.mockResolvedValue({ token: "novo", user })
    await expect(freshAccessToken(30_000, now)).resolves.toBe("novo")
  })

  it("answers the current token when the refresh fails", async () => {
    const token = jwt(now / 1000 + 10)
    setAccessToken(token)
    mockRefresh.mockRejectedValue(statusError(503))
    await expect(freshAccessToken(30_000, now)).resolves.toBe(token)
  })
})
