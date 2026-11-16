import { useEffect, useRef } from "react"
import { Link } from "react-router-dom"
import { tabsListVariants, tabsTriggerVariants } from "@/components/ui/tabs-variants"
import { cn } from "@/lib/utils"
import { areaPath, PELADA_AREAS, visibleAreas, type PeladaArea, type PeladaRole } from "@/utils/peladaAreas"

interface PeladaAreaTabsProps {
  peladaId: number | string
  current: PeladaArea
  role: PeladaRole
  className?: string
}

// The pelada's areas below md: underlined links in a strip that scrolls sideways, the current one scrolled into
// view. Switching is by tap only: no sideways swipe competes with the swipe back from the edge.
export function PeladaAreaTabs({ peladaId, current, role, className }: PeladaAreaTabsProps) {
  const stripRef = useRef<HTMLElement>(null)
  const activeRef = useRef<HTMLAnchorElement>(null)

  useEffect(() => {
    const strip = stripRef.current
    const active = activeRef.current
    if (!strip || !active) return
    // centre the current area without scrolling the page
    strip.scrollLeft = active.offsetLeft - (strip.clientWidth - active.offsetWidth) / 2
  }, [current])

  return (
    <nav ref={stripRef} aria-label="Áreas da pelada" className={cn("-mx-4 overflow-x-auto px-4 scrollbar-none", className)}>
      <div className={cn(tabsListVariants({ variant: "underline" }), "relative flex w-max min-w-full")}>
        {visibleAreas(role).map((area) => (
          <Link
            key={area}
            ref={area === current ? activeRef : undefined}
            to={areaPath(peladaId, area)}
            aria-current={area === current ? "page" : undefined}
            data-state={area === current ? "active" : undefined}
            className={tabsTriggerVariants({ variant: "underline" })}
          >
            {PELADA_AREAS[area].label}
          </Link>
        ))}
      </div>
    </nav>
  )
}
