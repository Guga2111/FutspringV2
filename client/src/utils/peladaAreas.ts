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

export type PeladaAreaGroup = "Pelada" | "Gestão"

export interface PeladaAreaInfo {
  label: string
  // segment after /pelada/:id ("" for the overview)
  segment: string
  group: PeladaAreaGroup
  adminOnly: boolean
  // one line for the overview shortcuts
  description: string
}

export const PELADA_AREAS: Record<PeladaArea, PeladaAreaInfo> = {
  overview: { label: "Visão geral", segment: "", group: "Pelada", adminOnly: false, description: "O que está acontecendo agora" },
  members: { label: "Membros", segment: "membros", group: "Pelada", adminOnly: false, description: "Jogadores, tipos e estrelas" },
  sessions: { label: "Sessões", segment: "sessoes", group: "Pelada", adminOnly: false, description: "Próxima sessão e histórico" },
  ranking: { label: "Ranking", segment: "ranking", group: "Pelada", adminOnly: false, description: "Gols, assistências e vitórias" },
  awards: { label: "Prêmios", segment: "premios", group: "Pelada", adminOnly: false, description: "Líderes de cada prêmio" },
  finance: { label: "Financeiro", segment: "financeiro", group: "Gestão", adminOnly: false, description: "Cobranças e caixa" },
  invitations: { label: "Convites", segment: "convites", group: "Gestão", adminOnly: true, description: "Link e convites individuais" },
  settings: { label: "Configurações", segment: "settings", group: "Gestão", adminOnly: true, description: "Horário, local e vagas" },
}

const AREA_ORDER: PeladaArea[] = ["overview", "members", "sessions", "ranking", "awards", "finance", "invitations", "settings"]

export function areaPath(peladaId: number | string, area: PeladaArea): string {
  const { segment } = PELADA_AREAS[area]
  return segment ? `/pelada/${peladaId}/${segment}` : `/pelada/${peladaId}`
}

// The area of a /pelada/:id/... pathname; null outside a pelada or on an unknown segment
export function areaFromPath(pathname: string): PeladaArea | null {
  const match = /^\/pelada\/[^/]+(?:\/([^/]+))?\/?$/.exec(pathname)
  if (!match) return null
  const segment = match[1] ?? ""
  return AREA_ORDER.find((area) => PELADA_AREAS[area].segment === segment) ?? null
}

// The areas a caller can open, in selector order; admin-only ones are only hidden (the server checks)
export function visibleAreas(isAdmin: boolean): PeladaArea[] {
  return AREA_ORDER.filter((area) => isAdmin || !PELADA_AREAS[area].adminOnly)
}

// True on /pelada/:id and every route below it
export function isPeladaPath(pathname: string, peladaId: number | string): boolean {
  const base = `/pelada/${peladaId}`
  return pathname === base || pathname.startsWith(`${base}/`)
}
