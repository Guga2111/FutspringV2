import { HugeiconsIcon } from "@hugeicons/react"
import { StarIcon } from "@hugeicons/core-free-icons"
import { cn } from "@/lib/utils"

// "4 ★" as a number and a filled gold star: on the landing's coloured fields the empty
// stars of StarRow (star-off) don't reach 3:1
export function StarCount({ stars, className, iconClassName }: { stars: number; className?: string; iconClassName?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-1 tabular-nums", className)} aria-label={`${stars} estrelas`}>
      {stars}
      <HugeiconsIcon icon={StarIcon} className={cn("size-3.5 fill-current text-gold", iconClassName)} aria-hidden />
    </span>
  )
}
