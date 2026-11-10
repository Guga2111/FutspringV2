import type { ReactNode } from "react"
import { cn } from "@/lib/utils"

interface TeamCardProps {
  // dot + name (or the rename input)
  title: ReactNode
  // stars average, final position…
  aside?: ReactNode
  children: ReactNode
  className?: string
}

// Card shell shared by the scheduled, live and finished team lists
export function TeamCard({ title, aside, children, className }: TeamCardProps) {
  return (
    <article className={cn("overflow-hidden rounded-xl border bg-card", className)}>
      <div className="flex items-center gap-2.5 border-b bg-card-elevated px-3.5 py-3">
        <div className="flex min-w-0 flex-1 items-center gap-2.5">{title}</div>
        {aside}
      </div>
      <ul className="px-3.5 py-1.5">{children}</ul>
    </article>
  )
}

export function TeamPlayerRow({ children, className }: { children: ReactNode; className?: string }) {
  return <li className={cn("flex items-center gap-2.5 py-[7px]", className)}>{children}</li>
}
