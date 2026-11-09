import { describe, expect, it } from "vitest"
import type { SubscriptionStatus, SubscriptionSummary } from "@/types/subscription"
import { isWritable, subscriptionNotice, subscriptionNoticeFrom } from "./subscription"

const ZONE = "America/Sao_Paulo"
const NOW = new Date("2026-11-04T18:00:00Z")
const DAY = 86_400_000

function sub(status: SubscriptionStatus, validUntil: string | null, writable = true): SubscriptionSummary {
  return { status, validUntil, writable }
}
const inDays = (days: number) => new Date(NOW.getTime() + days * DAY).toISOString()

describe("isWritable", () => {
  it("follows the server's flag and reads a missing row as writable", () => {
    expect(isWritable(sub("READ_ONLY", null, false))).toBe(false)
    expect(isWritable(sub("TRIAL", inDays(3)))).toBe(true)
    expect(isWritable(null)).toBe(true)
    expect(isWritable(undefined)).toBe(true)
  })
})

describe("subscriptionNotice", () => {
  it("shows the trial's end to the creator from 5 days before it, in the pelada's zone", () => {
    expect(subscriptionNotice(sub("TRIAL", inDays(5)), true, NOW, ZONE)).toEqual({
      text: "Teste grátis até 09/11",
      action: "Assinar",
      readOnly: false,
    })
    expect(subscriptionNotice(sub("TRIAL", new Date(NOW.getTime() + 5 * DAY + 1).toISOString()), true, NOW, ZONE)).toBeNull()
    // 02:00 UTC is still the day before in São Paulo
    expect(subscriptionNotice(sub("TRIAL", "2026-11-07T02:00:00Z"), true, NOW, ZONE)?.text).toBe("Teste grátis até 06/11")
  })

  it("never shows the trial or the grace to members", () => {
    expect(subscriptionNotice(sub("TRIAL", inDays(1)), false, NOW, ZONE)).toBeNull()
    expect(subscriptionNotice(sub("PAST_DUE", inDays(-2)), false, NOW, ZONE)).toBeNull()
  })

  it("tells the creator when a past-due pelada locks (7 days after validUntil)", () => {
    expect(subscriptionNotice(sub("PAST_DUE", "2026-11-02T18:00:00Z"), true, NOW, ZONE)).toEqual({
      text: "Pagamento pendente: a pelada trava em 09/11",
      action: "Renovar",
      readOnly: false,
    })
  })

  it("shows read-only and pending to everyone, with the action only for the creator", () => {
    for (const status of ["READ_ONLY", "PENDING"] as const) {
      expect(subscriptionNotice(sub(status, null, false), true, NOW, ZONE)).toEqual({
        text: "Pelada em somente leitura",
        action: "Assinar",
        readOnly: true,
      })
      expect(subscriptionNotice(sub(status, null, false), false, NOW, ZONE)).toEqual({
        text: "Pelada em somente leitura: o criador precisa renovar a assinatura",
        action: null,
        readOnly: true,
      })
    }
  })

  it("says nothing for active, exempt or a missing row", () => {
    expect(subscriptionNotice(sub("ACTIVE", inDays(1)), true, NOW, ZONE)).toBeNull()
    expect(subscriptionNotice(sub("EXEMPT", null), true, NOW, ZONE)).toBeNull()
    expect(subscriptionNotice(null, true, NOW, ZONE)).toBeNull()
  })
})

describe("subscriptionNoticeFrom", () => {
  it("is 5 days before the end of a trial, null otherwise", () => {
    expect(subscriptionNoticeFrom(sub("TRIAL", inDays(10)))).toBe(inDays(5))
    expect(subscriptionNoticeFrom(sub("ACTIVE", inDays(10)))).toBeNull()
    expect(subscriptionNoticeFrom(null)).toBeNull()
  })
})
