import { useEffect, useRef, useState, type ReactNode } from "react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import { Loading03Icon } from "@hugeicons/core-free-icons"
import type { RefreshHandler } from "@/context/mobile-shell-context-value"
import { OPEN_OVERLAY } from "@/hooks/useEdgeSwipeBack"
import { PULL_THRESHOLD, pullDistance, pullFires, swipeIntent } from "@/utils/gestures"
import { cn } from "@/lib/utils"

interface PullToRefreshProps {
  // the reads of the page on screen; none → the gesture does nothing
  handlers: () => RefreshHandler[]
  enabled: boolean
  children: ReactNode
}

// a touch inside its own scrolling area (the chat, a list with its own scroll) scrolls that area instead
function insideScrollArea(target: EventTarget | null, boundary: HTMLElement): boolean {
  let node = target instanceof Element ? target : null
  while (node && node !== boundary) {
    const { overflowY } = window.getComputedStyle(node)
    if ((overflowY === "auto" || overflowY === "scroll") && node.scrollHeight > node.clientHeight) return true
    node = node.parentElement
  }
  return false
}

function reducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches
}

// Pull down at the top of the page to read it again (the pages register their reads with useRefresh).
// Below md only; html has overscroll-behavior-y: none there, so Chrome's own reload (the whole app and the session
// boot) and Safari's bounce stay out of the way.
export function PullToRefresh({ handlers, enabled, children }: PullToRefreshProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [distance, setDistance] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  const refreshingRef = useRef(false)

  useEffect(() => {
    const container = containerRef.current
    if (!enabled || !container) return
    let start: { x: number; y: number } | null = null
    let pulling = false
    let current = 0

    const reset = () => {
      start = null
      pulling = false
      current = 0
    }

    const onStart = (event: TouchEvent) => {
      if (refreshingRef.current || event.touches.length !== 1 || window.scrollY > 0) return
      if (handlers().length === 0 || document.querySelector(OPEN_OVERLAY)) return
      if (insideScrollArea(event.target, container)) return
      start = { x: event.touches[0].clientX, y: event.touches[0].clientY }
    }

    const onMove = (event: TouchEvent) => {
      if (!start) return
      const dx = event.touches[0].clientX - start.x
      const dy = event.touches[0].clientY - start.y
      if (!pulling) {
        const intent = swipeIntent(dx, dy)
        if (intent === null) return
        if (intent === "horizontal" || dy < 0 || window.scrollY > 0) {
          reset()
          return
        }
        pulling = true
      }
      current = pullDistance(dy)
      setDistance(current)
    }

    const onEnd = () => {
      const fires = pulling && pullFires(current)
      reset()
      if (!fires) {
        setDistance(0)
        return
      }
      refreshingRef.current = true
      setRefreshing(true)
      setDistance(PULL_THRESHOLD)
      Promise.all(handlers().map((handler) => handler()))
        .catch(() => toast.error("Não foi possível atualizar"))
        .finally(() => {
          refreshingRef.current = false
          setRefreshing(false)
          setDistance(0)
        })
    }

    container.addEventListener("touchstart", onStart, { passive: true })
    container.addEventListener("touchmove", onMove, { passive: true })
    container.addEventListener("touchend", onEnd)
    container.addEventListener("touchcancel", onEnd)
    return () => {
      container.removeEventListener("touchstart", onStart)
      container.removeEventListener("touchmove", onMove)
      container.removeEventListener("touchend", onEnd)
      container.removeEventListener("touchcancel", onEnd)
    }
  }, [enabled, handlers])

  const still = reducedMotion()
  const offset = still ? 0 : distance
  const visible = distance > 0 || refreshing
  const progress = Math.min(1, distance / PULL_THRESHOLD)

  return (
    <div ref={containerRef} className="relative flex min-w-0 flex-1 flex-col">
      {visible && (
        <div
          role={refreshing ? "status" : undefined}
          aria-label={refreshing ? "Atualizando" : undefined}
          className="pointer-events-none absolute inset-x-0 top-0 z-20 flex justify-center"
          style={{ transform: `translateY(${still ? 12 : Math.max(0, offset - 40)}px)` }}
        >
          <span className="flex size-9 items-center justify-center rounded-full border bg-card text-brand-text shadow-menu">
            <HugeiconsIcon
              icon={Loading03Icon}
              className={cn("size-5", refreshing && !still && "animate-spin")}
              style={refreshing ? undefined : { transform: `rotate(${progress * 270}deg)`, opacity: 0.4 + progress * 0.6 }}
            />
          </span>
        </div>
      )}
      <div
        className={cn("flex min-w-0 flex-1 flex-col", distance === 0 && "transition-transform duration-200")}
        style={offset > 0 ? { transform: `translateY(${offset}px)` } : undefined}
      >
        {children}
      </div>
    </div>
  )
}
