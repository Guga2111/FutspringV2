import { useState } from 'react'

export function usePlayerSelection(initialSelected: number[] = []) {
  const [selected, setSelected] = useState<number[]>(initialSelected)

  function toggle(playerId: number) {
    setSelected(prev =>
      prev.includes(playerId) ? prev.filter(id => id !== playerId) : [...prev, playerId],
    )
  }

  function reset() {
    setSelected(initialSelected)
  }

  return { selected, toggle, reset }
}
