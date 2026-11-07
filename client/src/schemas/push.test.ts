import { describe, expect, it } from "vitest"
import { pushSubscriptionSchema } from "./push"

const valid = { endpoint: "https://fcm.googleapis.com/fcm/send/abc", keys: { p256dh: "BNc", auth: "tBH" } }

describe("pushSubscriptionSchema", () => {
  it("accepts a browser subscription and drops expirationTime", () => {
    expect(pushSubscriptionSchema.parse({ ...valid, expirationTime: null })).toEqual(valid)
  })

  it("requires the endpoint and both keys", () => {
    expect(pushSubscriptionSchema.safeParse({ ...valid, endpoint: "" }).success).toBe(false)
    expect(pushSubscriptionSchema.safeParse({ endpoint: valid.endpoint, keys: { p256dh: "BNc" } }).success).toBe(false)
    expect(pushSubscriptionSchema.safeParse({ endpoint: valid.endpoint }).success).toBe(false)
  })

  it("mirrors the DTO's sizes", () => {
    expect(pushSubscriptionSchema.safeParse({ ...valid, endpoint: "x".repeat(1025) }).success).toBe(false)
    expect(pushSubscriptionSchema.safeParse({ ...valid, keys: { p256dh: "x".repeat(201), auth: "a" } }).success).toBe(false)
    expect(pushSubscriptionSchema.safeParse({ ...valid, keys: { p256dh: "p", auth: "x".repeat(51) } }).success).toBe(false)
    expect(pushSubscriptionSchema.safeParse({ ...valid, endpoint: "x".repeat(1024) }).success).toBe(true)
  })
})
