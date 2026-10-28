import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export interface SettingsSection {
  id: string
  label: string
}

const SMOOTH_SCROLL_MS = 1000

// "Nesta página": one link per card, the card in view highlighted. Scrolls with scrollIntoView instead of a
// #hash, so the router's location (and ScrollToTop) doesn't change.
export function SettingsNav({ sections }: { sections: SettingsSection[] }) {
  const [active, setActive] = useState(sections[0]?.id ?? null)
  // A clicked link stays highlighted during its smooth scroll, even when the card can't reach the top
  // (the last cards of the page); the next scroll by the user takes over again
  const clickedAt = useRef(0)

  useEffect(() => {
    // The active card is the last one whose top has passed the threshold; at the very bottom of the page the
    // last cards can't reach it, so the last one wins. Captured so it works whatever element scrolls.
    function update() {
      if (Date.now() - clickedAt.current < SMOOTH_SCROLL_MS) return
      const doc = document.documentElement
      if (window.innerHeight + window.scrollY >= doc.scrollHeight - 2) {
        setActive(sections[sections.length - 1]?.id ?? null)
        return
      }
      // the top 30% of the viewport
      const threshold = window.innerHeight * 0.3
      let current = sections[0]?.id ?? null
      for (const section of sections) {
        const top = document.getElementById(section.id)?.getBoundingClientRect().top
        if (top !== undefined && top <= threshold) current = section.id
      }
      setActive(current)
    }
    window.addEventListener("scroll", update, { passive: true, capture: true })
    return () => window.removeEventListener("scroll", update, { capture: true })
  }, [sections])

  return (
    <nav aria-label="Nesta página" className="flex flex-col gap-2">
      <p className="px-3 text-xs font-semibold uppercase tracking-wider text-faint-foreground">Nesta página</p>
      <ul className="flex flex-col gap-0.5">
        {sections.map((section) => (
          <li key={section.id}>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              aria-current={active === section.id ? "true" : undefined}
              className={cn(
                "w-full justify-start font-normal text-muted-foreground",
                active === section.id && "bg-accent font-medium text-foreground",
              )}
              onClick={() => {
                clickedAt.current = Date.now()
                setActive(section.id)
                document.getElementById(section.id)?.scrollIntoView({ behavior: "smooth", block: "start" })
              }}
            >
              {section.label}
            </Button>
          </li>
        ))}
      </ul>
    </nav>
  )
}
