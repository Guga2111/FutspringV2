import { describe, expect, it } from "vitest"
import type { PeladaResponse } from "@/types/pelada"
import { createActions, createIntent, isChatPath, isTabRoot, parentPath, peladaIdFromPath, tabForPath } from "@/utils/mobileNav"

const ME = 7

function pelada(id: number, patch: Partial<PeladaResponse> = {}): PeladaResponse {
  return {
    id,
    name: `Pelada ${id}`,
    dayOfWeek: "THURSDAY",
    timeOfDay: "21:00",
    duration: 1.5,
    address: null,
    reference: null,
    image: null,
    autoCreateDailyEnabled: false,
    memberCount: 10,
    numberOfTeams: 2,
    playersPerTeam: 5,
    timeZone: "America/Sao_Paulo",
    isAdmin: false,
    isFinanceManager: false,
    nextDaily: null,
    subscription: null,
    ...patch,
  }
}

const READ_ONLY = { status: "READ_ONLY" as const, validUntil: null, writable: false }

describe("tabForPath", () => {
  it.each([
    ["/home", "home"],
    ["/invitations/3", "home"],
    ["/peladas", "peladas"],
    ["/pelada/1", "peladas"],
    ["/pelada/1/financeiro/9", "peladas"],
    ["/pelada/1/chat", "peladas"],
    ["/daily/4", "peladas"],
    ["/notificacoes", "notifications"],
    [`/profile/${ME}`, "profile"],
    ["/settings", "profile"],
    ["/admin", "profile"],
    ["/admin/peladas/2", "profile"],
    ["/profile/99", null],
  ])("%s → %s", (path, tab) => {
    expect(tabForPath(path, ME)).toBe(tab)
  })
})

describe("isTabRoot", () => {
  it("is true only on each tab's own page", () => {
    expect(isTabRoot("/home", ME)).toBe(true)
    expect(isTabRoot("/peladas/", ME)).toBe(true)
    expect(isTabRoot("/notificacoes", ME)).toBe(true)
    expect(isTabRoot(`/profile/${ME}`, ME)).toBe(true)
    expect(isTabRoot("/pelada/1", ME)).toBe(false)
    expect(isTabRoot("/settings", ME)).toBe(false)
    expect(isTabRoot("/profile/99", ME)).toBe(false)
  })
})

describe("parentPath", () => {
  it.each([
    ["/daily/4", "/peladas"],
    ["/pelada/1/financeiro/9", "/pelada/1/financeiro"],
    ["/pelada/1/financeiro/9/paguei", "/pelada/1/financeiro/9"],
    ["/pelada/1/chat", "/pelada/1"],
    ["/pelada/1/membros", "/pelada/1"],
    ["/pelada/1/settings", "/pelada/1"],
    ["/pelada/1/assinatura", "/pelada/1"],
    ["/pelada/1", "/peladas"],
    ["/settings", `/profile/${ME}`],
    ["/admin", `/profile/${ME}`],
    ["/admin/peladas/2", "/admin"],
    ["/invitations/3", "/home"],
    ["/profile/99", "/home"],
  ])("%s → %s", (path, parent) => {
    expect(parentPath(path, ME)).toBe(parent)
  })

  it("has no parent on a tab root", () => {
    expect(parentPath("/home", ME)).toBeNull()
    expect(parentPath(`/profile/${ME}`, ME)).toBeNull()
  })
})

describe("peladaIdFromPath / isChatPath", () => {
  it("reads the pelada of a pelada route", () => {
    expect(peladaIdFromPath("/pelada/12/sessoes")).toBe(12)
    expect(peladaIdFromPath("/daily/12")).toBeNull()
    expect(peladaIdFromPath("/pelada/abc")).toBeNull()
  })

  it("recognizes the chat route", () => {
    expect(isChatPath("/pelada/1/chat")).toBe(true)
    expect(isChatPath("/pelada/1/membros")).toBe(false)
  })
})

describe("createActions", () => {
  const kinds = (peladas: PeladaResponse[], current: number | null = null) =>
    createActions(peladas, current).map((a) => a.kind)

  it("offers a plain player only Criar pelada", () => {
    expect(kinds([pelada(1), pelada(2)])).toEqual(["pelada"])
  })

  it("offers an admin sessions and invitations, a responsável charges", () => {
    expect(kinds([pelada(1, { isAdmin: true })])).toEqual(["session", "invite", "pelada"])
    expect(kinds([pelada(1, { isFinanceManager: true })])).toEqual(["charge", "pelada"])
  })

  it("leaves out writes of a read-only pelada but keeps the invitation", () => {
    const readOnly = pelada(1, { isAdmin: true, isFinanceManager: true, subscription: READ_ONLY })
    expect(kinds([readOnly])).toEqual(["invite", "pelada"])
  })

  it("goes straight to the only eligible pelada", () => {
    const [session] = createActions([pelada(1, { isAdmin: true }), pelada(2)], null)
    expect(session.peladas.map((p) => p.id)).toEqual([1])
    expect(session.target?.id).toBe(1)
  })

  it("asks which pelada when several qualify, unless one is on screen", () => {
    const peladas = [pelada(1, { isAdmin: true }), pelada(2, { isAdmin: true })]
    expect(createActions(peladas, null)[0].target).toBeNull()
    expect(createActions(peladas, 2)[0].target?.id).toBe(2)
    // the pelada on screen doesn't qualify: the choice stays
    expect(createActions([...peladas, pelada(3)], 3)[0].target).toBeNull()
  })
})

describe("createIntent", () => {
  it("reads the router state a '+' item navigates with", () => {
    expect(createIntent({ create: "charge" })).toBe("charge")
    expect(createIntent({ create: "pelada" })).toBeNull()
    expect(createIntent(null)).toBeNull()
    expect(createIntent("session")).toBeNull()
  })
})
