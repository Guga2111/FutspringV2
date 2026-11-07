import { describe, expect, it } from "vitest"
import { base64UrlToBytes, sameServerKey, subscriptionRequest } from "./push"

describe("base64UrlToBytes", () => {
  it("decodes base64url without padding", () => {
    // "-_8" is 0xFB 0xFF (base64 "+/8=")
    expect(Array.from(base64UrlToBytes("-_8"))).toEqual([0xfb, 0xff])
    expect(Array.from(base64UrlToBytes("AQID"))).toEqual([1, 2, 3])
  })
})

describe("sameServerKey", () => {
  it("compares the subscription's key with the server's", () => {
    expect(sameServerKey(new Uint8Array([1, 2, 3]).buffer, "AQID")).toBe(true)
    expect(sameServerKey(new Uint8Array([1, 2, 4]).buffer, "AQID")).toBe(false)
    expect(sameServerKey(new Uint8Array([1, 2]).buffer, "AQID")).toBe(false)
  })

  it("assumes the same key when the browser doesn't expose it", () => {
    expect(sameServerKey(null, "AQID")).toBe(true)
  })
})

describe("subscriptionRequest", () => {
  it("keeps endpoint and keys", () => {
    const json = { endpoint: "https://web.push.apple.com/x", expirationTime: null, keys: { p256dh: "p", auth: "a" } }
    expect(subscriptionRequest(json)).toEqual({ endpoint: json.endpoint, keys: { p256dh: "p", auth: "a" } })
  })

  it("rejects a subscription without keys", () => {
    expect(subscriptionRequest({ endpoint: "https://web.push.apple.com/x" })).toBeNull()
  })
})
