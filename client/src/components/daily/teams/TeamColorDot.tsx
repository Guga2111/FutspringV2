import { cn } from "@/lib/utils"

// Team color comes from the backend; the ring keeps white and black teams visible on both themes
export function TeamColorDot({ color, className }: { color: string | null; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn("inline-block size-2.5 shrink-0 rounded-full border border-team-dot-border bg-muted-foreground", className)}
      style={color ? { background: color } : undefined}
    />
  )
}
