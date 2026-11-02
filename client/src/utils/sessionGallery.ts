import { endOfDay, format, startOfDay } from "date-fns"
import type { DailyListItem } from "@/types/daily"
import { parseLocalDate } from "@/utils/dates"

// The caller's result filter of the session gallery
export type GalleryResult = "ALL" | "WON" | "NOT_WON"

export const GALLERY_RESULTS: GalleryResult[] = ["ALL", "WON", "NOT_WON"]

export const galleryResultLabel: Record<GalleryResult, string> = {
  ALL: "Todas",
  WON: "Vitórias",
  NOT_WON: "Sem vitória",
}

// A calendar range (react-day-picker's DateRange shape); `to` missing means a single day
export interface GalleryRange {
  from?: Date
  to?: Date
}

const matchesResult: Record<GalleryResult, (daily: DailyListItem) => boolean> = {
  ALL: () => true,
  WON: (daily) => daily.wonSession === true,
  NOT_WON: (daily) => daily.wonSession === false,
}

// Finished sessions only, newest first
export function gallerySessions(dailies: DailyListItem[]): DailyListItem[] {
  return dailies
    .filter((d) => d.status === "FINISHED")
    .sort((a, b) => b.dailyDate.localeCompare(a.dailyDate) || b.id - a.id)
}

// Both ends inclusive; no range keeps everything
export function inRange(daily: DailyListItem, range: GalleryRange | undefined): boolean {
  if (!range?.from) return true
  const date = parseLocalDate(daily.dailyDate)
  return date >= startOfDay(range.from) && date <= endOfDay(range.to ?? range.from)
}

export function filterGallery(
  dailies: DailyListItem[],
  filter: { result: GalleryResult; range: GalleryRange | undefined },
): DailyListItem[] {
  return dailies.filter((d) => matchesResult[filter.result](d) && inRange(d, filter.range))
}

// How many sessions each result filter shows inside the range, so the counts always match the grid
export function galleryCounts(dailies: DailyListItem[], range: GalleryRange | undefined): Record<GalleryResult, number> {
  const inside = dailies.filter((d) => inRange(d, range))
  return {
    ALL: inside.length,
    WON: inside.filter(matchesResult.WON).length,
    NOT_WON: inside.filter(matchesResult.NOT_WON).length,
  }
}

// "Todo o período", "08/10/2026", "01/10 – 31/10/2026" or "15/12/2025 – 10/01/2026"
export function formatRangeLabel(range: GalleryRange | undefined): string {
  if (!range?.from) return "Todo o período"
  const { from } = range
  const to = range.to ?? from
  if (format(from, "yyyy-MM-dd") === format(to, "yyyy-MM-dd")) return format(from, "dd/MM/yyyy")
  if (from.getFullYear() === to.getFullYear()) return `${format(from, "dd/MM")} – ${format(to, "dd/MM/yyyy")}`
  return `${format(from, "dd/MM/yyyy")} – ${format(to, "dd/MM/yyyy")}`
}

// "1 sessão", "12 sessões"
export function formatSessionCount(count: number): string {
  return `${count} ${count === 1 ? "sessão" : "sessões"}`
}
