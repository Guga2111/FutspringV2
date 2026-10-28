import {
  ArrowRightLeft,
  BadgeCheck,
  BadgeMinus,
  BellRing,
  CalendarClock,
  CalendarPlus,
  CalendarX,
  Flag,
  ShieldCheck,
  ShieldOff,
  Shuffle,
  Trophy,
  UserCheck,
  UserMinus,
  UserPlus,
  UserX,
  type LucideIcon,
} from "lucide-react"
import type { NotificationAward, NotificationResponse, NotificationType } from "@/types/notification"
import { formatWeekdayDayMonth } from "@/utils/dates"

// A piece of a notification's text; names are bold
export interface NotificationTextPart {
  text: string
  bold?: boolean
}

export interface NotificationView {
  parts: NotificationTextPart[]
  // where a click goes; null when there's nowhere to go (removed from the pelada)
  href: string | null
  icon: LucideIcon
}

export const notificationAwardLabel: Record<NotificationAward, string> = {
  PUSKAS: "Puskás",
  WILTBALL: "Bola Murcha",
  ARTILHEIRO: "Artilheiro",
  GARCOM: "Garçom",
}

const b = (text: string): NotificationTextPart => ({ text, bold: true })
const t = (text: string): NotificationTextPart => ({ text })

const peladaName = (n: NotificationResponse) => n.pelada?.name ?? "pelada"
const actorName = (n: NotificationResponse) => n.actor?.username ?? "Um admin"
// "sáb, 31/10" inside a sentence
const sessionDate = (n: NotificationResponse) => (n.daily ? formatWeekdayDayMonth(n.daily.date).toLowerCase() : "")
const sessionDateTime = (n: NotificationResponse) => (n.daily ? `${sessionDate(n)} às ${n.daily.time}` : "")

// The session when there is one, else the pelada
function defaultHref(n: NotificationResponse): string | null {
  if (n.daily) return `/daily/${n.daily.id}`
  if (n.pelada) return `/pelada/${n.pelada.id}`
  return null
}

function awardParts(n: NotificationResponse): NotificationTextPart[] {
  const label = n.award ? notificationAwardLabel[n.award] : "prêmio"
  const date = sessionDate(n)
  switch (n.award) {
    case "WILTBALL":
      return [t("Você levou a "), b(label), t(` de ${date}`)]
    case "PUSKAS":
      return [t("Você ganhou o "), b(label), t(` de ${date} 🏆`)]
    default:
      return [t("Você foi o "), b(label), t(` de ${date} 🏆`)]
  }
}

type ViewBuilder = (n: NotificationResponse) => Omit<NotificationView, "href"> & { href?: string | null }

// One entry per type: a new backend type fails the build until it gets its text
const views: Record<NotificationType, ViewBuilder> = {
  DIARISTA_BUMPED: (n) => ({
    icon: UserX,
    parts: [t("Você perdeu a vaga em "), b(peladaName(n)), t(` (${sessionDate(n)}) para `), b(actorName(n))],
  }),
  SESSION_CANCELED: (n) => ({
    icon: CalendarX,
    parts: [t(`A sessão de ${sessionDate(n)} da `), b(peladaName(n)), t(" foi cancelada")],
  }),
  ATTENDANCE_REMOVED_BY_ADMIN: (n) => ({
    icon: UserMinus,
    parts: [b(actorName(n)), t(` removeu sua presença na sessão de ${sessionDate(n)}`)],
  }),
  ATTENDANCE_CONFIRMED_BY_ADMIN: (n) => ({
    icon: UserCheck,
    parts: [b(actorName(n)), t(` confirmou sua presença na sessão de ${sessionDate(n)}`)],
  }),
  SESSION_REMINDER: (n) => ({
    icon: BellRing,
    parts: [t(`Sessão de ${sessionDateTime(n)} na `), b(peladaName(n)), t(" e você ainda não confirmou")],
  }),
  SESSION_CREATED: (n) => ({
    icon: CalendarPlus,
    parts: [t("Nova sessão na "), b(peladaName(n)), t(`: ${sessionDateTime(n)}`)],
  }),
  DIARISTA_WINDOW_OPEN: (n) => ({
    icon: CalendarClock,
    parts: [t("As confirmações abriram na "), b(peladaName(n)), t(`: ${sessionDateTime(n)}`)],
  }),
  TEAMS_SORTED: (n) => ({
    icon: Shuffle,
    parts: n.team
      ? [t(`Times sorteados para ${sessionDate(n)}: você está no `), b(n.team.name)]
      : [t(`Times sorteados para ${sessionDate(n)} na `), b(peladaName(n))],
  }),
  SESSION_FINISHED: (n) => ({
    icon: Flag,
    parts: [t(`Sessão de ${sessionDate(n)} da `), b(peladaName(n)), t(" finalizada: veja o resultado")],
  }),
  ADDED_TO_PELADA: (n) => ({
    icon: UserPlus,
    parts: n.actor ? [b(actorName(n)), t(" adicionou você à "), b(peladaName(n))] : [t("Você entrou na "), b(peladaName(n))],
  }),
  REMOVED_FROM_PELADA: (n) => ({
    icon: UserMinus,
    href: null,
    parts: [t("Você foi removido da "), b(peladaName(n))],
  }),
  ADMIN_GRANTED: (n) => ({
    icon: ShieldCheck,
    parts: [t("Você agora é admin da "), b(peladaName(n))],
  }),
  ADMIN_REVOKED: (n) => ({
    icon: ShieldOff,
    parts: [t("Você não é mais admin da "), b(peladaName(n))],
  }),
  MENSALISTA_GRANTED: (n) => ({
    icon: BadgeCheck,
    parts: [t("Você agora é mensalista da "), b(peladaName(n)), t(": confirme a qualquer momento")],
  }),
  MENSALISTA_REVOKED: (n) => ({
    icon: BadgeMinus,
    parts: [t("Você agora é diarista da "), b(peladaName(n))],
  }),
  PELADA_TRANSFERRED: (n) => ({
    icon: ArrowRightLeft,
    parts: [b(actorName(n)), t(" transferiu a "), b(peladaName(n)), t(" para você")],
  }),
  AWARD_WON: (n) => ({
    icon: Trophy,
    parts: awardParts(n),
  }),
}

export function notificationView(n: NotificationResponse): NotificationView {
  const view = views[n.type](n)
  return { ...view, href: view.href === undefined ? defaultHref(n) : view.href }
}

export function notificationPlainText(n: NotificationResponse): string {
  return notificationView(n).parts.map((p) => p.text).join("")
}

// Appends a page to the list, without repeating a notification already there (pages can overlap)
export function mergeNotifications(current: NotificationResponse[], page: NotificationResponse[]): NotificationResponse[] {
  const known = new Set(current.map((n) => n.id))
  return [...current, ...page.filter((n) => !known.has(n.id))]
}

// A pushed notification goes to the top; a collapsed one (same id, re-sorted teams) replaces the old entry
export function upsertNotification(current: NotificationResponse[], n: NotificationResponse): NotificationResponse[] {
  return [n, ...current.filter((c) => c.id !== n.id)]
}

// Pushed types that also show a toast with "Ver": losing or not having a spot. The rest only update the bell
export const notificationToast: Record<NotificationType, boolean> = {
  DIARISTA_BUMPED: true,
  SESSION_CANCELED: true,
  ATTENDANCE_REMOVED_BY_ADMIN: true,
  SESSION_REMINDER: true,
  ATTENDANCE_CONFIRMED_BY_ADMIN: false,
  SESSION_CREATED: false,
  DIARISTA_WINDOW_OPEN: false,
  TEAMS_SORTED: false,
  SESSION_FINISHED: false,
  ADDED_TO_PELADA: false,
  REMOVED_FROM_PELADA: false,
  ADMIN_GRANTED: false,
  ADMIN_REVOKED: false,
  MENSALISTA_GRANTED: false,
  MENSALISTA_REVOKED: false,
  PELADA_TRANSFERRED: false,
  AWARD_WON: false,
}
