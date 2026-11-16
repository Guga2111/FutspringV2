import { useCallback, useRef, useState } from "react"

// A read that reloads by bumping a version (the effect re-runs) can still hand back a promise: reload() resolves
// when the read of the new version calls settle(), whatever its outcome (the hook keeps its own error state).
// Pull to refresh waits for it; nobody needs to await it otherwise.
export function useReload() {
  const [version, setVersion] = useState(0)
  const waiting = useRef<Array<() => void>>([])

  const reload = useCallback(
    () =>
      new Promise<void>((resolve) => {
        waiting.current.push(resolve)
        setVersion((v) => v + 1)
      }),
    [],
  )

  // called by the latest read when it answers (an older, cancelled read never calls it)
  const settle = useCallback(() => {
    for (const resolve of waiting.current.splice(0)) resolve()
  }, [])

  return { version, reload, settle }
}
