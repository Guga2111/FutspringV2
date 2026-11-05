import { describe, it, expect } from "vitest"
import { formatInZone, formatNextSessionShort, formatRangeLabel, formatShortDate, formatWeekdayDayMonth, parseLocalDate, todayInZone } from "@/utils/dates"

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

describe("formatInZone", () => {
  it("shows the same instant in the pelada's zone", () => {
    expect(formatInZone("2026-10-29T23:00:00Z", "America/Sao_Paulo")).toBe("qui, 29/10 às 20:00")
    expect(formatInZone("2026-10-29T23:00:00Z", "America/Toronto")).toBe("qui, 29/10 às 19:00")
  })

  it("crosses midnight into the next day", () => {
    expect(formatInZone("2026-10-31T02:30:00Z", "America/Sao_Paulo")).toBe("sex, 30/10 às 23:30")
  })
})

describe("formatShortDate", () => {
  const now = new Date(2026, 10, 4)
  it("drops the year inside the current one", () => {
    expect(formatShortDate("2026-11-10", now)).toBe("10/11")
  })
  it("keeps the year for another year", () => {
    expect(formatShortDate("2025-12-28", now)).toBe("28/12/2025")
  })
})

describe("formatRangeLabel", () => {
  it("names the period", () => {
    expect(formatRangeLabel(undefined)).toBe("Todo o período")
    expect(formatRangeLabel({ from: new Date(2026, 9, 8) })).toBe("08/10/2026")
    expect(formatRangeLabel({ from: new Date(2026, 9, 1), to: new Date(2026, 9, 31) })).toBe("01/10 – 31/10/2026")
    expect(formatRangeLabel({ from: new Date(2025, 11, 15), to: new Date(2026, 0, 10) })).toBe("15/12/2025 – 10/01/2026")
  })
})

describe("todayInZone", () => {
  it("reads the calendar day in the given zone", () => {
    // 02:00 UTC on Nov 5 is still Nov 4 in São Paulo
    const instant = new Date("2026-11-05T02:00:00Z")
    expect(todayInZone("America/Sao_Paulo", instant)).toBe("2026-11-04")
    expect(todayInZone("Europe/Lisbon", instant)).toBe("2026-11-05")
  })
})
