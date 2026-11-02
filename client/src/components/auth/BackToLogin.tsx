import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowLeft01Icon } from "@hugeicons/core-free-icons"
import { Link } from "react-router-dom"

export function BackToLogin() {
  return (
    <Link
      to="/auth?tab=login"
      className="inline-flex items-center gap-2 self-start rounded-sm text-body text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <HugeiconsIcon icon={ArrowLeft01Icon} aria-hidden className="size-4" />
      Voltar para o login
    </Link>
  )
}
