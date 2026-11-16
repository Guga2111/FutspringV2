// The decisions behind the mobile gestures (swipe back from the edge, pull to refresh), kept pure for the tests

// a swipe back only starts this close to the left edge, so it never competes with the area strip,
// carousels or the teams editor's drag and drop
export const EDGE_WIDTH = 24
// how far the finger moves before the gesture decides between horizontal and vertical
export const INTENT_DISTANCE = 10
// releasing past this share of the screen width, or faster than this, completes the swipe back
export const BACK_DISTANCE_RATIO = 0.35
export const BACK_VELOCITY = 0.5 // px/ms
// the pull fires from this distance (after the resistance)
export const PULL_THRESHOLD = 64
const PULL_RESISTANCE = 2
// the indicator stops here however far the finger goes
export const PULL_MAX = 96

export type Intent = "horizontal" | "vertical" | null

export function startsAtEdge(x: number): boolean {
  return x <= EDGE_WIDTH
}

// null while the finger hasn't moved enough to tell
export function swipeIntent(dx: number, dy: number): Intent {
  if (Math.max(Math.abs(dx), Math.abs(dy)) < INTENT_DISTANCE) return null
  return Math.abs(dx) > Math.abs(dy) ? "horizontal" : "vertical"
}

export function velocity(distance: number, elapsedMs: number): number {
  return elapsedMs > 0 ? distance / elapsedMs : 0
}

export function completesBack(dx: number, width: number, pxPerMs: number): boolean {
  if (dx <= 0) return false
  return dx > width * BACK_DISTANCE_RATIO || pxPerMs > BACK_VELOCITY
}

// how far the page follows a downward pull of dy pixels
export function pullDistance(dy: number): number {
  return Math.min(PULL_MAX, Math.max(0, dy / PULL_RESISTANCE))
}

export function pullFires(distance: number): boolean {
  return distance >= PULL_THRESHOLD
}
