import { cn } from "@/lib/utils"

interface StarRowProps {
  stars: number
  className?: string
  // pending players in the attendance list use a dimmer gold
  muted?: boolean
}

// ★★★☆☆ as filled (gold) and empty stars
export function StarRow({ stars, className, muted = false }: StarRowProps) {
  const filled = Math.max(0, Math.min(5, Math.round(stars)))
  return (
    <span className={cn("whitespace-nowrap text-xs tracking-[1px]", className)} aria-label={`${filled} de 5 estrelas`}>
      <span className={muted ? "text-gold/60" : "text-gold"}>{"★".repeat(filled)}</span>
      <span className="text-star-off">{"★".repeat(5 - filled)}</span>
    </span>
  )
}
