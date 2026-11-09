// Areas of a pelada workspace: one URL each, grouped in the local area selector
export type PeladaArea =
  | "overview"
  | "members"
  | "sessions"
  | "ranking"
  | "awards"
  | "finance"
  | "invitations"
  | "settings"
  | "subscription"

export type PeladaAreaGroup = "Pelada" | "Gestão"

// The caller's relationship with the pelada: the creator is also an admin, an admin also a member
export type PeladaRole = "member" | "admin" | "creator"

const ROLE_RANK: Record<PeladaRole, number> = { member: 0, admin: 1, creator: 2 }

export function peladaRole(isAdmin: boolean, isCreator: boolean): PeladaRole {
  return isCreator ? "creator" : isAdmin ? "admin" : "member"
}

export interface PeladaAreaInfo {
  label: string
  // segment after /pelada/:id ("" for the overview)
  segment: string
  group: PeladaAreaGroup
  // who sees it in the navigation (the server checks every route anyway)
  access: PeladaRole
  // one line for the overview shortcuts
  description: string
}

export const PELADA_AREAS: Record<PeladaArea, PeladaAreaInfo> = {
  overview: { label: "Visão geral", segment: "", group: "Pelada", access: "member", description: "O que está acontecendo agora" },
  members: { label: "Membros", segment: "membros", group: "Pelada", access: "member", description: "Jogadores, tipos e estrelas" },
  sessions: { label: "Sessões", segment: "sessoes", group: "Pelada", access: "member", description: "Próxima sessão e histórico" },
  ranking: { label: "Ranking", segment: "ranking", group: "Pelada", access: "member", description: "Gols, assistências e vitórias" },
  awards: { label: "Prêmios", segment: "premios", group: "Pelada", access: "member", description: "Líderes de cada prêmio" },
  finance: { label: "Financeiro", segment: "financeiro", group: "Gestão", access: "member", description: "Cobranças e caixa" },
  invitations: { label: "Convites", segment: "convites", group: "Gestão", access: "admin", description: "Link e convites individuais" },
  settings: { label: "Configurações", segment: "settings", group: "Gestão", access: "admin", description: "Horário, local e vagas" },
  subscription: { label: "Assinatura", segment: "assinatura", group: "Gestão", access: "creator", description: "Plano, Pix e pagamentos" },
}

const AREA_ORDER: PeladaArea[] = ["overview", "members", "sessions", "ranking", "awards", "finance", "invitations", "settings", "subscription"]

export function areaPath(peladaId: number | string, area: PeladaArea): string {
  const { segment } = PELADA_AREAS[area]
  return segment ? `/pelada/${peladaId}/${segment}` : `/pelada/${peladaId}`
}

// The area of a /pelada/:id/... pathname; null outside a pelada or on an unknown segment.
// One sub-path is an area too: a collection of the finance area (/pelada/:id/financeiro/:listId)
export function areaFromPath(pathname: string): PeladaArea | null {
  const match = /^\/pelada\/[^/]+(?:\/([^/]+)(?:\/([^/]+))?)?\/?$/.exec(pathname)
  if (!match) return null
  const segment = match[1] ?? ""
  if (match[2] !== undefined) return segment === PELADA_AREAS.finance.segment && /^\d+$/.test(match[2]) ? "finance" : null
  return AREA_ORDER.find((area) => PELADA_AREAS[area].segment === segment) ?? null
}

// Whether a caller with this role sees the area in the navigation
export function canSeeArea(area: PeladaArea, role: PeladaRole): boolean {
  return ROLE_RANK[role] >= ROLE_RANK[PELADA_AREAS[area].access]
}

// The areas a caller can open, in selector order; the others are only hidden (the server checks)
export function visibleAreas(role: PeladaRole): PeladaArea[] {
  return AREA_ORDER.filter((area) => canSeeArea(area, role))
}

// True on /pelada/:id and every route below it
export function isPeladaPath(pathname: string, peladaId: number | string): boolean {
  const base = `/pelada/${peladaId}`
  return pathname === base || pathname.startsWith(`${base}/`)
}
