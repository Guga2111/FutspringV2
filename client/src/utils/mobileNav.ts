import type { PeladaResponse } from "@/types/pelada"
import { isWritable } from "@/utils/subscription"

// Navigation of the mobile shell (below md): the bottom tabs, the header's ← and the "+" menu

export type MobileTab = "home" | "peladas" | "notifications" | "profile"

export function tabRoot(tab: MobileTab, userId: number | null): string {
  const roots: Record<MobileTab, string> = {
    home: "/home",
    peladas: "/peladas",
    notifications: "/notificacoes",
    profile: userId === null ? "/settings" : `/profile/${userId}`,
  }
  return roots[tab]
}

const PELADA_PATH = /^\/pelada\/([^/]+)(?:\/(.*))?$/

// The tab a route belongs to; null for a route of no tab (another player's profile)
export function tabForPath(pathname: string, userId: number | null): MobileTab | null {
  const path = trimSlash(pathname)
  if (path === "/home" || path.startsWith("/invitations/")) return "home"
  if (path === "/peladas" || PELADA_PATH.test(path) || path.startsWith("/daily/")) return "peladas"
  if (path === "/notificacoes") return "notifications"
  if (path === "/settings" || path === "/admin" || path.startsWith("/admin/")) return "profile"
  if (userId !== null && path === `/profile/${userId}`) return "profile"
  return null
}

// A tab's own page: no ← in the header, no swipe back
export function isTabRoot(pathname: string, userId: number | null): boolean {
  const tab = tabForPath(pathname, userId)
  return tab !== null && trimSlash(pathname) === tabRoot(tab, userId)
}

// Where ← goes when there is no history to go back to (a WhatsApp link, a push, a reload).
// A session's pelada isn't in its URL: the session page passes it to the header once loaded.
export function parentPath(pathname: string, userId: number | null): string | null {
  const path = trimSlash(pathname)
  if (isTabRoot(path, userId)) return null
  const pelada = PELADA_PATH.exec(path)
  if (pelada) {
    const [, id, rest] = pelada
    if (!rest) return "/peladas"
    const parts = rest.split("/")
    // a collection and its quick payment notice
    if (parts[0] === "financeiro" && parts.length === 3) return `/pelada/${id}/financeiro/${parts[1]}`
    if (parts[0] === "financeiro" && parts.length === 2) return `/pelada/${id}/financeiro`
    return `/pelada/${id}`
  }
  if (path.startsWith("/daily/")) return "/peladas"
  if (path.startsWith("/admin/")) return "/admin"
  if (path === "/settings" || path === "/admin") return tabRoot("profile", userId)
  return "/home"
}

// The pelada a /pelada/:id/... route is in
export function peladaIdFromPath(pathname: string): number | null {
  const match = PELADA_PATH.exec(trimSlash(pathname))
  if (!match || !/^\d+$/.test(match[1])) return null
  return Number(match[1])
}

export function isChatPath(pathname: string): boolean {
  return /^\/pelada\/[^/]+\/chat\/?$/.test(pathname)
}

export type CreateActionKind = "session" | "invite" | "charge" | "pelada"

export const CREATE_ACTION_LABEL: Record<CreateActionKind, string> = {
  session: "Nova sessão",
  invite: "Convidar jogador",
  charge: "Nova cobrança",
  pelada: "Criar pelada",
}

export interface CreateAction {
  kind: CreateActionKind
  // the peladas this action can run in (empty for "Criar pelada")
  peladas: PeladaResponse[]
  // the pelada on screen when it qualifies, or the only one that does: no "Em qual pelada?" step
  target: PeladaResponse | null
}

const ELIGIBLE: Record<Exclude<CreateActionKind, "pelada">, (p: PeladaResponse) => boolean> = {
  session: (p) => p.isAdmin && isWritable(p.subscription),
  invite: (p) => p.isAdmin,
  charge: (p) => p.isFinanceManager && isWritable(p.subscription),
}

// What the "+" offers: only what the caller's roles allow (the server still checks every action)
export function createActions(peladas: PeladaResponse[], currentPeladaId: number | null): CreateAction[] {
  const actions: CreateAction[] = []
  for (const kind of ["session", "invite", "charge"] as const) {
    const eligible = peladas.filter(ELIGIBLE[kind])
    if (eligible.length === 0) continue
    const current = eligible.find((p) => p.id === currentPeladaId) ?? null
    actions.push({ kind, peladas: eligible, target: current ?? (eligible.length === 1 ? eligible[0] : null) })
  }
  actions.push({ kind: "pelada", peladas: [], target: null })
  return actions
}

function trimSlash(pathname: string): string {
  return pathname.length > 1 && pathname.endsWith("/") ? pathname.slice(0, -1) : pathname
}

// What a "+" item asks the page it opens: the pelada layout / the finance area open the form once
export type CreateIntent = Exclude<CreateActionKind, "pelada">
export interface CreateNavState {
  create: CreateIntent
}

export function createIntent(state: unknown): CreateIntent | null {
  if (typeof state !== "object" || state === null || !("create" in state)) return null
  const value = (state as { create: unknown }).create
  return value === "session" || value === "invite" || value === "charge" ? value : null
}
