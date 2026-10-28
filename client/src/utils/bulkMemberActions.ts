import type { PeladaMember } from "@/types/pelada"

// Bulk actions of the members grid: explicit pairs (never a toggle), since a selection can mix both states
export type BulkMemberAction = "makeAdmin" | "removeAdmin" | "makeMensalista" | "makeDiarista" | "setStars" | "remove"

export const BULK_MEMBER_ACTIONS: BulkMemberAction[] = [
  "makeAdmin",
  "removeAdmin",
  "makeMensalista",
  "makeDiarista",
  "setStars",
  "remove",
]

export const bulkActionLabel: Record<BulkMemberAction, string> = {
  makeAdmin: "Tornar admin",
  removeAdmin: "Remover admin",
  makeMensalista: "Tornar mensalista",
  makeDiarista: "Tornar diarista",
  setStars: "Definir estrelas",
  remove: "Remover da pelada",
}

export interface BulkActionPlan {
  // the selected members the action changes (the label counts only these)
  targets: PeladaMember[]
  // why the action is disabled; null when it can run
  hint: string | null
}

export interface BulkPlanContext {
  creatorId: number | null
  currentUserId: number | null
  // mensalistas in the whole pelada, not only in the selection
  mensalistaCount: number
  maxMensalistas: number
}

export function pluralMembers(count: number): string {
  return count === 1 ? "1 membro" : `${count} membros`
}

/** What each bulk action would do with the selection. The server re-checks every rule. */
export function planBulkActions(
  selected: PeladaMember[],
  { creatorId, currentUserId, mensalistaCount, maxMensalistas }: BulkPlanContext,
): Record<BulkMemberAction, BulkActionPlan> {
  const isCreator = (m: PeladaMember) => m.id === creatorId
  const isSelf = (m: PeladaMember) => m.id === currentUserId

  const makeAdmin = selected.filter((m) => !m.isAdmin && !isCreator(m))
  const removeAdmin = selected.filter((m) => m.isAdmin && !isCreator(m) && !isSelf(m))
  const makeMensalista = selected.filter((m) => !m.isMensalista)
  const makeDiarista = selected.filter((m) => m.isMensalista)
  const remove = selected.filter((m) => !isCreator(m) && !isSelf(m))
  const room = Math.max(0, maxMensalistas - mensalistaCount)

  return {
    makeAdmin: { targets: makeAdmin, hint: makeAdmin.length === 0 ? "Todos já são admins" : null },
    removeAdmin: { targets: removeAdmin, hint: removeAdmin.length === 0 ? "Nenhum admin removível" : null },
    makeMensalista: {
      targets: makeMensalista,
      hint:
        makeMensalista.length === 0
          ? "Todos já são mensalistas"
          : makeMensalista.length > room
            ? `Cabem ${room}, você selecionou ${makeMensalista.length}`
            : null,
    },
    makeDiarista: { targets: makeDiarista, hint: makeDiarista.length === 0 ? "Todos já são diaristas" : null },
    setStars: { targets: selected, hint: null },
    remove: { targets: remove, hint: remove.length === 0 ? "O criador e você não podem ser removidos aqui" : null },
  }
}

/** "o criador e você não entram" when Remover skips them; null when nobody selected is skipped. */
export function removeSkipNote(selected: PeladaMember[], creatorId: number | null, currentUserId: number | null) {
  const creator = selected.some((m) => m.id === creatorId)
  const self = selected.some((m) => m.id === currentUserId && m.id !== creatorId)
  if (creator && self) return "o criador e você não entram"
  if (creator) return currentUserId === creatorId ? "você não entra" : "o criador não entra"
  if (self) return "você não entra"
  return null
}

/** "Ana, Bia, Caio, Duda, Edu +3" */
export function previewNames(members: PeladaMember[], max = 5): string {
  const names = members.slice(0, max).map((m) => m.username).join(", ")
  return members.length > max ? `${names} +${members.length - max}` : names
}

/** The same stars for every member, or null when they differ. */
export function sharedStars(members: PeladaMember[]): number | null {
  const first = members[0]?.stars
  return first !== undefined && members.every((m) => m.stars === first) ? first : null
}
