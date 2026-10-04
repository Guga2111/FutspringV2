// Pelada.dayOfWeek is sent and stored as a java.time.DayOfWeek name; labels are pt-BR
export const DAYS_OF_WEEK = [
  { value: "MONDAY", label: "Segunda" },
  { value: "TUESDAY", label: "Terça" },
  { value: "WEDNESDAY", label: "Quarta" },
  { value: "THURSDAY", label: "Quinta" },
  { value: "FRIDAY", label: "Sexta" },
  { value: "SATURDAY", label: "Sábado" },
  { value: "SUNDAY", label: "Domingo" },
] as const

export type DayOfWeek = (typeof DAYS_OF_WEEK)[number]["value"]

// Older peladas may still hold a Portuguese label until migration V5 runs; show it as is
export function dayOfWeekLabel(value: string): string {
  return DAYS_OF_WEEK.find((d) => d.value === value)?.label ?? value
}
