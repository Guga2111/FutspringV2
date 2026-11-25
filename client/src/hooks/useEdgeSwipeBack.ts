import { useEffect, type RefObject } from "react"
import { usePwaInstall } from "@/hooks/usePwaInstall"
import { completesBack, startsAtEdge, swipeIntent, velocity } from "@/utils/gestures"
import { isIosDevice } from "@/utils/pwaPlatform"

export const OPEN_OVERLAY = '[role="dialog"][data-state="open"], [role="alertdialog"][data-state="open"]'

function reducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches
}

// Whether the swipe back belongs to us: only in the iPhone's installed app. Safari in the browser already swipes
// back on its own, and Android's system gesture / button already fires popstate.
export function useSwipeBackAvailable(): boolean {
  const { context } = usePwaInstall()
  return context === "installed" && isIosDevice(navigator.userAgent, navigator.maxTouchPoints)
}

// Swipe from the left edge to go back, the page following the finger over a veil (the previous page isn't kept
// mounted, so the veil is what shows behind it). Does the same as the header's ←.
export function useEdgeSwipeBack(
  pageRef: RefObject<HTMLElement | null>,
  veilRef: RefObject<HTMLElement | null>,
  { enabled, onBack }: { enabled: boolean; onBack: () => void },
) {
  useEffect(() => {
    const page = pageRef.current
    const veil = veilRef.current
    if (!enabled || !page || !veil) return

    let start: { x: number; y: number; time: number } | null = null
    let tracking = false
    let dx = 0

    const paint = (offset: number, animate: boolean) => {
      const transition = animate ? "transform 200ms cubic-bezier(0.2, 0.8, 0.2, 1)" : "none"
      page.style.transition = transition
      page.style.transform = offset > 0 ? `translateX(${offset}px)` : ""
      veil.style.transition = animate ? "opacity 200ms ease-out" : "none"
      veil.style.opacity = String(offset > 0 ? Math.max(0, 1 - offset / window.innerWidth) : 0)
    }

    const reset = () => {
      start = null
      tracking = false
      dx = 0
    }

    const onStart = (event: TouchEvent) => {
      const touch = event.touches[0]
      if (event.touches.length !== 1 || !startsAtEdge(touch.clientX) || document.querySelector(OPEN_OVERLAY)) return
      start = { x: touch.clientX, y: touch.clientY, time: event.timeStamp }
    }

    const onMove = (event: TouchEvent) => {
      if (!start) return
      const touch = event.touches[0]
      dx = touch.clientX - start.x
      if (!tracking) {
        const intent = swipeIntent(dx, touch.clientY - start.y)
        if (intent === null) return
        if (intent === "vertical" || dx < 0) {
          reset()
          return
        }
        tracking = true
      }
      event.preventDefault()
      if (!reducedMotion()) paint(Math.max(0, dx), false)
    }

    const onEnd = (event: TouchEvent) => {
      if (!start || !tracking) {
        reset()
        return
      }
      const done = completesBack(dx, window.innerWidth, velocity(dx, event.timeStamp - start.time))
      reset()
      if (!done) {
        paint(0, true)
        return
      }
      if (reducedMotion()) {
        onBack()
        return
      }
      paint(window.innerWidth, true)
      window.setTimeout(() => {
        onBack()
        paint(0, false)
      }, 200)
    }

    document.addEventListener("touchstart", onStart, { passive: true })
    document.addEventListener("touchmove", onMove, { passive: false })
    document.addEventListener("touchend", onEnd)
    document.addEventListener("touchcancel", onEnd)
    return () => {
      document.removeEventListener("touchstart", onStart)
      document.removeEventListener("touchmove", onMove)
      document.removeEventListener("touchend", onEnd)
      document.removeEventListener("touchcancel", onEnd)
      paint(0, false)
    }
  }, [enabled, onBack, pageRef, veilRef])
}
