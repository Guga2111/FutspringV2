// How a position in a table is shown, the same in every ranking (pelada ranking, attendance,
// live and final league tables): the podium as "1º" in gold / silver / bronze, the rest as a plain number
const MEDALS = ["text-gold", "text-silver", "text-bronze"] as const

export interface RankStyle {
  label: string
  className: string
}

// onPodium is false when the top positions earned nothing yet (attendance with no session played)
export function rankStyle(position: number, onPodium = true): RankStyle {
  if (onPodium && position >= 1 && position <= MEDALS.length) {
    return { label: `${position}º`, className: `font-bold ${MEDALS[position - 1]}` }
  }
  return { label: String(position), className: "text-subtle-foreground" }
}
