import { useEffect, useRef, useState, type RefObject } from "react"
import { Link, useLocation } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowLeft01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/hooks/useAuth"
import { useMobileShell } from "@/hooks/useMobileShell"
import { isTabRoot } from "@/utils/mobileNav"
import { cn } from "@/lib/utils"

interface MobileHeaderProps {
  // the page's content: its first h1 gives the compact title and decides when it shows
  contentRef: RefObject<HTMLElement | null>
  onActionsSlot: (element: HTMLDivElement | null) => void
}

// The page's first h1: its text and whether it is still on screen below the header (iOS "large title": the big
// title stays in the page and the compact one appears here once it scrolls away)
function usePageHeading(contentRef: RefObject<HTMLElement | null>, headerRef: RefObject<HTMLElement | null>) {
  const { pathname } = useLocation()
  const [heading, setHeading] = useState<{ text: string; visible: boolean } | null>(null)

  useEffect(() => {
    const content = contentRef.current
    if (!content) return
    let observed: Element | null = null
    let intersection: IntersectionObserver | null = null

    const watch = () => {
      const h1 = content.querySelector("h1")
      if (h1 === observed) {
        if (h1) setHeading((prev) => (prev && prev.text !== h1.textContent ? { ...prev, text: h1.textContent ?? "" } : prev))
        return
      }
      intersection?.disconnect()
      observed = h1
      if (!h1) {
        setHeading(null)
        return
      }
      const top = headerRef.current?.offsetHeight ?? 56
      intersection = new IntersectionObserver(
        ([entry]) => setHeading({ text: h1.textContent ?? "", visible: entry.isIntersecting }),
        { rootMargin: `-${top}px 0px 0px 0px` },
      )
      intersection.observe(h1)
    }

    watch()
    // pages render their h1 after their data arrives
    const mutations = new MutationObserver(watch)
    mutations.observe(content, { childList: true, subtree: true, characterData: true })
    return () => {
      mutations.disconnect()
      intersection?.disconnect()
    }
  }, [contentRef, headerRef, pathname])

  return heading
}

// Top of every private page below md (replaces the old top bar with the hamburger): ← and the title (no ← on a tab's
// own page; Início shows the logo), and the page's actions on the right
export function MobileHeader({ contentRef, onActionsSlot }: MobileHeaderProps) {
  const shell = useMobileShell()
  const { pathname } = useLocation()
  const { user } = useAuth()
  const headerRef = useRef<HTMLElement>(null)
  const heading = usePageHeading(contentRef, headerRef)
  const root = isTabRoot(pathname, user?.id ?? null)
  const home = pathname === "/home"
  const config = shell?.header ?? {}
  const title = config.title ?? heading?.text ?? ""
  // a title the page set always shows (the pelada's name); otherwise the h1's text once it scrolls away, or at once
  // when the page has no h1 (or is still loading). Tab roots too: their big h1 is the title until it scrolls.
  const showTitle = config.title !== undefined || !heading?.visible

  return (
    <header
      ref={headerRef}
      className="sticky top-0 z-30 shrink-0 border-b bg-card pt-[env(safe-area-inset-top)] md:hidden"
    >
      <div className="flex h-14 items-center gap-1 pl-2 pr-2">
        {home ? (
          <Link to="/home" className="flex flex-1 items-center gap-2.5 pl-2">
            <img src="/logo-64.webp" alt="" width={28} height={28} className="size-7 rounded-full object-cover" />
            <span className="text-title font-bold text-brand-text">FutSpring</span>
          </Link>
        ) : (
          <>
            {!root && (
              <Button
                variant="ghost"
                size="icon"
                aria-label="Voltar"
                onClick={() => shell?.goBack()}
                className="size-10 shrink-0 rounded-[10px] hover:bg-accent [&_svg]:size-[22px]"
              >
                <HugeiconsIcon icon={ArrowLeft01Icon} />
              </Button>
            )}
            <div
              className={cn(
                "flex min-w-0 flex-1 flex-col transition-opacity duration-150",
                root && "pl-2",
                showTitle ? "opacity-100" : "opacity-0",
              )}
              aria-hidden={!showTitle}
            >
              <span className="truncate text-title font-semibold leading-tight">{title}</span>
              {config.subtitle && <span className="truncate text-caption text-muted-foreground">{config.subtitle}</span>}
            </div>
          </>
        )}
        <div ref={onActionsSlot} className="flex shrink-0 items-center gap-0.5" />
      </div>
    </header>
  )
}
