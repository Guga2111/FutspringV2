import { describe, it, expect } from "vitest"
import { planBulkActions, pluralMembers, previewNames, removeSkipNote, sharedStars } from "./bulkMemberActions"
import type { PeladaMember } from "@/types/pelada"

const member = (id: number, overrides: Partial<PeladaMember> = {}): PeladaMember => ({
  id,
  username: `m${id}`,
  image: null,
  stars: 3,
  position: null,
  isAdmin: false,
  isMensalista: false,
  ...overrides,
})

// 1 is the creator (admin), 2 the caller (admin), 3 a mensalista, 4 a diarista
const creator = member(1, { isAdmin: true })
const self = member(2, { isAdmin: true })
const mensalista = member(3, { isMensalista: true })
const diarista = member(4)
const ctx = { creatorId: 1, currentUserId: 2, mensalistaCount: 1, maxMensalistas: 3 }

const ids = (members: PeladaMember[]) => members.map((m) => m.id)

describe("planBulkActions", () => {
  it("counts only the members each action changes", () => {
    const plan = planBulkActions([creator, self, mensalista, diarista], ctx)

    expect(ids(plan.makeAdmin.targets)).toEqual([3, 4])
    expect(ids(plan.removeAdmin.targets)).toEqual([])
    expect(ids(plan.makeMensalista.targets)).toEqual([1, 2, 4])
    expect(ids(plan.makeDiarista.targets)).toEqual([3])
    expect(ids(plan.setStars.targets)).toEqual([1, 2, 3, 4])
    expect(ids(plan.remove.targets)).toEqual([3, 4])
  })

  it("never demotes or removes the creator or the caller", () => {
    const plan = planBulkActions([creator, self], ctx)

    expect(plan.removeAdmin.hint).toBe("Nenhum admin removível")
    expect(plan.remove.targets).toEqual([])
    expect(plan.remove.hint).not.toBeNull()
    expect(plan.makeAdmin.hint).toBe("Todos já são admins")
  })

  it("demotes other admins", () => {
    const other = member(5, { isAdmin: true })
    expect(ids(planBulkActions([other, self], ctx).removeAdmin.targets)).toEqual([5])
  })

  it("blocks mensalistas over the limit and names the room left", () => {
    const plan = planBulkActions([creator, self, diarista], ctx)

    expect(plan.makeMensalista.hint).toBe("Cabem 2, você selecionou 3")
    expect(planBulkActions([self, diarista], ctx).makeMensalista.hint).toBeNull()
    expect(planBulkActions([mensalista], ctx).makeMensalista.hint).toBe("Todos já são mensalistas")
    expect(planBulkActions([diarista], ctx).makeDiarista.hint).toBe("Todos já são diaristas")
  })

  it("never reports negative room", () => {
    const plan = planBulkActions([diarista], { ...ctx, mensalistaCount: 5, maxMensalistas: 3 })
    expect(plan.makeMensalista.hint).toBe("Cabem 0, você selecionou 1")
  })
})

describe("removeSkipNote", () => {
  it("names who Remover leaves out", () => {
    expect(removeSkipNote([creator, self, diarista], 1, 2)).toBe("o criador e você não entram")
    expect(removeSkipNote([creator, diarista], 1, 2)).toBe("o criador não entra")
    expect(removeSkipNote([self, diarista], 1, 2)).toBe("você não entra")
    expect(removeSkipNote([creator, diarista], 1, 1)).toBe("você não entra")
    expect(removeSkipNote([diarista], 1, 2)).toBeNull()
  })
})

describe("labels", () => {
  it("pluralizes members", () => {
    expect(pluralMembers(1)).toBe("1 membro")
    expect(pluralMembers(3)).toBe("3 membros")
  })

  it("previews up to five names, then the rest as a count", () => {
    const many = [1, 2, 3, 4, 5, 6, 7, 8].map((id) => member(id))
    expect(previewNames(many)).toBe("m1, m2, m3, m4, m5 +3")
    expect(previewNames(many.slice(0, 2))).toBe("m1, m2")
  })

  it("finds the stars every member shares", () => {
    expect(sharedStars([member(1, { stars: 4 }), member(2, { stars: 4 })])).toBe(4)
    expect(sharedStars([member(1, { stars: 4 }), member(2)])).toBeNull()
    expect(sharedStars([])).toBeNull()
  })
})
