import type { ReactNode } from "react"
import { HugeiconsIcon, type IconSvgElement } from "@hugeicons/react"

interface AuthHeaderProps {
  icon: IconSvgElement
  title: string
  children: ReactNode
}

// Centered header of the single-purpose auth screens (forgot password, link sent): icon tile, title, explanation
export function AuthHeader({ icon, title, children }: AuthHeaderProps) {
  return (
    <header className="flex flex-col items-center gap-3 text-center">
      <span className="mb-1 flex size-12 items-center justify-center rounded-tile bg-card">
        <HugeiconsIcon icon={icon} aria-hidden className="size-6 text-brand-text" />
      </span>
      <h1 className="font-display text-heading font-bold leading-none md:text-heading-lg">{title}</h1>
      <p className="text-body-lg text-muted-foreground">{children}</p>
    </header>
  )
}
