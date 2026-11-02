import { describe, expect, it } from "vitest"
import type { Invitation } from "@/types/invitation"
import { invitationTimeLabel, relativeToNow } from "@/utils/invitations"

const NOW = new Date("2026-11-04T18:00:00.000Z")
const user = { id: 1, username: "Leal", image: null, position: null }
function invitation(patch: Partial<Invitation>): Invitation {
  return {
    id: 1, peladaId: 3, pelada: { id: 3, name: "Pelada", image: null, dayOfWeek: "TUESDAY", timeOfDay: "19:30" },
    recipient: user, sender: user, status: "PENDING", createdAt: "2026-11-03T18:00:00.000Z",
    expiresAt: "2026-11-09T18:00:00.000Z", respondedAt: null, canceledAt: null, isMember: false, ...patch,
  }
}

describe("relativeToNow", () => {
  it("speaks of the future and the past in pt-BR", () => {
    expect(relativeToNow("2026-11-09T18:00:00.000Z", NOW)).toBe("em 5 dias")
    expect(relativeToNow("2026-11-04T16:00:00.000Z", NOW)).toBe("há 2 horas")
  })
})

describe("invitationTimeLabel", () => {
  it("shows the expiry of a pending invitation relative to now", () => {
    expect(invitationTimeLabel(invitation({}), NOW)).toBe("Expira em 5 dias")
  })
  it("names how a closed invitation ended", () => {
    expect(invitationTimeLabel(invitation({ status: "EXPIRED" }), NOW)).toMatch(/^Expirou em \d{2}\/11 às/)
    expect(invitationTimeLabel(invitation({ status: "CANCELED", canceledAt: "2026-11-04T12:00:00.000Z" }), NOW)).toMatch(/^Cancelado em 04\/11/)
    expect(invitationTimeLabel(invitation({ status: "ACCEPTED", respondedAt: "2026-11-04T12:00:00.000Z" }), NOW)).toMatch(/^Respondido em 04\/11/)
  })
})
