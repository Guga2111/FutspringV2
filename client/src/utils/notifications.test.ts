import { describe, it, expect } from "vitest"
import { Trophy, UserX } from "lucide-react"
import type { NotificationResponse, NotificationType } from "@/types/notification"
import { notificationTypeCategory } from "@/types/notification"
import { mergeNotifications, notificationPlainText, notificationView } from "./notifications"

function make(type: NotificationType, overrides: Partial<NotificationResponse> = {}): NotificationResponse {
  return {
    id: 1,
    type,
    category: notificationTypeCategory[type],
    award: null,
    createdAt: "2026-10-28T12:00:00Z",
    readAt: null,
    pelada: { id: 10, name: "Pelada do Fut", image: null },
    // 2026-10-31 is a Saturday
    daily: { id: 100, date: "2026-10-31", time: "20:00" },
    actor: { id: 1, username: "Leal", image: null, position: null },
    team: null,
    ...overrides,
  }
}

const noDaily = { daily: null }

// Text and link for every type: the Record fails the build when a type is missing, this checks the content
const cases: [NotificationType, Partial<NotificationResponse>, string, string | null][] = [
  ["DIARISTA_BUMPED", {}, "Você perdeu a vaga em Pelada do Fut (sáb, 31/10) para Leal", "/daily/100"],
  ["SESSION_CANCELED", {}, "A sessão de sáb, 31/10 da Pelada do Fut foi cancelada", "/daily/100"],
  ["ATTENDANCE_REMOVED_BY_ADMIN", {}, "Leal removeu sua presença na sessão de sáb, 31/10", "/daily/100"],
  ["ATTENDANCE_CONFIRMED_BY_ADMIN", {}, "Leal confirmou sua presença na sessão de sáb, 31/10", "/daily/100"],
  ["SESSION_REMINDER", { actor: null }, "Sessão de sáb, 31/10 às 20:00 na Pelada do Fut e você ainda não confirmou", "/daily/100"],
  ["SESSION_CREATED", {}, "Nova sessão na Pelada do Fut: sáb, 31/10 às 20:00", "/daily/100"],
  ["DIARISTA_WINDOW_OPEN", { actor: null }, "As confirmações abriram na Pelada do Fut: sáb, 31/10 às 20:00", "/daily/100"],
  ["TEAMS_SORTED", { team: { name: "Time Azul", color: "blue" } }, "Times sorteados para sáb, 31/10: você está no Time Azul", "/daily/100"],
  ["SESSION_FINISHED", {}, "Sessão de sáb, 31/10 da Pelada do Fut finalizada: veja o resultado", "/daily/100"],
  ["ADDED_TO_PELADA", noDaily, "Leal adicionou você à Pelada do Fut", "/pelada/10"],
  ["REMOVED_FROM_PELADA", noDaily, "Você foi removido da Pelada do Fut", null],
  ["ADMIN_GRANTED", noDaily, "Você agora é admin da Pelada do Fut", "/pelada/10"],
  ["ADMIN_REVOKED", noDaily, "Você não é mais admin da Pelada do Fut", "/pelada/10"],
  ["MENSALISTA_GRANTED", noDaily, "Você agora é mensalista da Pelada do Fut: confirme a qualquer momento", "/pelada/10"],
  ["MENSALISTA_REVOKED", noDaily, "Você agora é diarista da Pelada do Fut", "/pelada/10"],
  ["PELADA_TRANSFERRED", noDaily, "Leal transferiu a Pelada do Fut para você", "/pelada/10"],
  ["AWARD_WON", { award: "ARTILHEIRO", actor: null }, "Você foi o Artilheiro de sáb, 31/10 🏆", "/daily/100"],
]

describe("notificationView", () => {
  it("covers every type", () => {
    expect(new Set(cases.map(([type]) => type))).toEqual(new Set(Object.keys(notificationTypeCategory)))
  })

  it.each(cases)("%s", (type, overrides, text, href) => {
    const n = make(type, overrides)
    const view = notificationView(n)
    expect(notificationPlainText(n)).toBe(text)
    expect(view.href).toBe(href)
    expect(view.icon).toBeTruthy()
  })

  it("puts names in bold", () => {
    const { parts, icon } = notificationView(make("DIARISTA_BUMPED"))
    expect(parts.filter((p) => p.bold).map((p) => p.text)).toEqual(["Pelada do Fut", "Leal"])
    expect(icon).toBe(UserX)
  })

  it("names each award", () => {
    expect(notificationPlainText(make("AWARD_WON", { award: "WILTBALL" }))).toBe("Você levou a Bola Murcha de sáb, 31/10")
    expect(notificationPlainText(make("AWARD_WON", { award: "PUSKAS" }))).toBe("Você ganhou o Puskás de sáb, 31/10 🏆")
    expect(notificationPlainText(make("AWARD_WON", { award: "GARCOM" }))).toBe("Você foi o Garçom de sáb, 31/10 🏆")
    expect(notificationView(make("AWARD_WON", { award: "GARCOM" })).icon).toBe(Trophy)
  })

  it("falls back when the actor is gone or the team is unknown", () => {
    expect(notificationPlainText(make("ATTENDANCE_REMOVED_BY_ADMIN", { actor: null }))).toBe(
      "Um admin removeu sua presença na sessão de sáb, 31/10",
    )
    expect(notificationPlainText(make("TEAMS_SORTED"))).toBe("Times sorteados para sáb, 31/10 na Pelada do Fut")
    expect(notificationPlainText(make("ADDED_TO_PELADA", { actor: null, daily: null }))).toBe("Você entrou na Pelada do Fut")
  })
})

describe("mergeNotifications", () => {
  it("appends a page without repeating ids", () => {
    const a = make("SESSION_CREATED", { id: 1 })
    const b = make("SESSION_CREATED", { id: 2 })
    const c = make("SESSION_CREATED", { id: 3 })
    expect(mergeNotifications([a, b], [b, c]).map((n) => n.id)).toEqual([1, 2, 3])
  })
})
