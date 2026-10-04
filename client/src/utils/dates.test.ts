import { describe, it, expect } from "vitest"
import { formatNextSessionShort, formatWeekdayDayMonth, parseLocalDate } from "./dates"

describe("parseLocalDate", () => {
  it("reads the date in local time", () => {
    const date = parseLocalDate("2026-10-08")
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 9, 8])
  })
})

describe("formatWeekdayDayMonth", () => {
  it("formats a capitalized short weekday with day/month", () => {
    expect(formatWeekdayDayMonth("2026-10-08")).toBe("Qui, 08/10")
    expect(formatWeekdayDayMonth("2026-10-04")).toBe("Dom, 04/10")
  })
})

describe("formatNextSessionShort", () => {
  it("joins date and time", () => {
    expect(formatNextSessionShort({ date: "2026-10-08", time: "21:00" })).toBe("Qui, 08/10 · 21:00")
  })

  it("says there is no session", () => {
    expect(formatNextSessionShort(null)).toBe("Sem sessão agendada")
  })
})
