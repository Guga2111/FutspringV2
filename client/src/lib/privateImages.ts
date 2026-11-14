import { getImage } from "@/api/files"

interface ImageEntry {
  listeners: Set<() => void>
  controller: AbortController
  url?: string
}

// Memory only, shared by mounted consumers of this account/file. Never persisted or served to another account.
const images = new Map<string, ImageEntry>()
const imageKey = (userId: number, filename: string) => `${userId}:${filename}`

export function imageSnapshot(userId: number, filename: string): string | undefined {
  return images.get(imageKey(userId, filename))?.url
}

export function subscribeImage(userId: number, filename: string, listener: () => void): () => void {
  const key = imageKey(userId, filename)
  let entry = images.get(key)
  if (!entry) {
    entry = { listeners: new Set(), controller: new AbortController() }
    images.set(key, entry)
    const pending = entry
    void getImage(filename, pending.controller.signal).then((blob) => {
      // An abort can race an already-resolved HTTP response: don't create an orphan URL.
      if (images.get(key) !== pending) return
      pending.url = URL.createObjectURL(blob)
      pending.listeners.forEach((notify) => notify())
    }).catch(() => { /* Forbidden, deleted or canceled photos retain the component's fallback. */ })
  }
  // A subscription has its own callback identity, even if two consumers pass the same listener.
  const notify = () => listener()
  entry.listeners.add(notify)
  const retained = entry
  return () => {
    retained.listeners.delete(notify)
    if (retained.listeners.size !== 0 || images.get(key) !== retained) return
    images.delete(key)
    retained.controller.abort()
    if (retained.url) URL.revokeObjectURL(retained.url)
  }
}
