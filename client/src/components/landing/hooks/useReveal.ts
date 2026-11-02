import { useEffect, useRef, type CSSProperties } from "react"

// Reveals a section's [data-reveal] children once, when the section enters the viewport
// (classes in index.css). Content is visible until the hook arms the section, so it never
// stays hidden without script, and nothing is armed under reduced motion or without
// IntersectionObserver.
export function useReveal<T extends HTMLElement>() {
  const ref = useRef<T>(null)

  useEffect(() => {
    const element = ref.current
    if (!element || typeof IntersectionObserver === "undefined") return
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return

    element.classList.add("reveal-armed")
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          element.classList.add("reveal-in")
          observer.disconnect()
        }
      },
      { threshold: 0.15 },
    )
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  return ref
}

// the stagger position of a revealed child: style={revealAt(2)}
export function revealAt(index: number) {
  return { "--reveal-index": index } as CSSProperties
}
