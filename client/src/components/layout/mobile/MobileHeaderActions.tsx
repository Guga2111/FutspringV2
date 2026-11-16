import type { ReactNode } from "react"
import { createPortal } from "react-dom"
import { Link } from "react-router-dom"
import { HugeiconsIcon, type IconSvgElement } from "@hugeicons/react"
import { Button } from "@/components/ui/button"
import { useMobileShell } from "@/hooks/useMobileShell"

// A page's buttons on the right of the mobile header (the gear, the chat), rendered where the page decides them.
// The header is hidden from md, so these only show on phones.
export function MobileHeaderActions({ children }: { children: ReactNode }) {
  const slot = useMobileShell()?.actionsSlot
  return slot ? createPortal(children, slot) : null
}

// An icon link of the mobile header (48 px target with the header's padding)
export function MobileHeaderLink({ to, label, icon }: { to: string; label: string; icon: IconSvgElement }) {
  return (
    <Button asChild variant="ghost" size="icon" className="size-10 rounded-[10px] hover:bg-accent [&_svg]:size-[22px]">
      <Link to={to} aria-label={label}>
        <HugeiconsIcon icon={icon} />
      </Link>
    </Button>
  )
}
