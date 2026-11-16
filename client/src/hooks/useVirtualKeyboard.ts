import { useSyncExternalStore } from "react"

// the visual viewport shrinks by at least this much when the on-screen keyboard opens (a browser bar is smaller)
const KEYBOARD_MIN_HEIGHT = 150

function subscribe(onChange: () => void) {
  const viewport = window.visualViewport
  if (!viewport) return () => undefined
  viewport.addEventListener("resize", onChange)
  return () => viewport.removeEventListener("resize", onChange)
}

function keyboardOpen(): boolean {
  const viewport = window.visualViewport
  return viewport !== null && window.innerHeight - viewport.height > KEYBOARD_MIN_HEIGHT
}

// Whether the phone's keyboard is open (the bottom tabs step aside, as WhatsApp's do)
export function useVirtualKeyboard(): boolean {
  return useSyncExternalStore(subscribe, keyboardOpen, () => false)
}
