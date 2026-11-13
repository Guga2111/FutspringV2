import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
vi.mock("@/api/files", () => ({ getImage: vi.fn() }))
import { getImage } from "@/api/files"
import { imageSnapshot, subscribeImage } from "@/lib/privateImages"

const load = vi.mocked(getImage)
let releases: (() => void)[]
function subscribe(id: number, name: string, listener = vi.fn()) {
  const release = subscribeImage(id, name, listener)
  releases.push(release)
  return release
}
async function settle() { await Promise.resolve(); await Promise.resolve() }
beforeEach(() => {
  releases = []
  load.mockReset()
  vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:photo")
  vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {})
})
afterEach(() => { releases.forEach((release) => release()); vi.restoreAllMocks() })

describe("mounted private image ownership", () => {
  it("shares one request and one object URL; only the last consumer releases them", async () => {
    load.mockResolvedValue(new Blob())
    const changed = vi.fn()
    const first = subscribe(1, "same.png", changed)
    const second = subscribe(1, "same.png", changed)
    await settle()
    expect(load).toHaveBeenCalledTimes(1)
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    expect(changed).toHaveBeenCalledTimes(2)
    expect(imageSnapshot(1, "same.png")).toBe("blob:photo")
    first()
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
    second()
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:photo")
    expect(imageSnapshot(1, "same.png")).toBeUndefined()
  })

  it("aborts the final release and ignores its late reply, even after a new subscription", async () => {
    let answer: (blob: Blob) => void = () => undefined
    load.mockReturnValueOnce(new Promise((resolve) => { answer = resolve })).mockResolvedValueOnce(new Blob())
    const release = subscribe(1, "late.png")
    const signal = load.mock.calls[0][1]
    release()
    expect(signal?.aborted).toBe(true)
    subscribe(1, "late.png")
    await settle()
    answer(new Blob())
    await settle()
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    expect(imageSnapshot(1, "late.png")).toBe("blob:photo")
  })

  it("never shares bytes between different accounts", async () => {
    load.mockResolvedValue(new Blob())
    vi.mocked(URL.createObjectURL).mockReturnValueOnce("blob:one").mockReturnValueOnce("blob:two")
    subscribe(1, "photo.png")
    subscribe(2, "photo.png")
    await settle()
    expect(load).toHaveBeenCalledTimes(2)
    expect(imageSnapshot(1, "photo.png")).toBe("blob:one")
    expect(imageSnapshot(2, "photo.png")).toBe("blob:two")
  })

  it("keeps failures on the fallback and permits a fresh request after release", async () => {
    load.mockRejectedValueOnce(new Error("forbidden")).mockResolvedValueOnce(new Blob())
    const release = subscribe(1, "missing.png")
    await settle()
    expect(imageSnapshot(1, "missing.png")).toBeUndefined()
    release()
    subscribe(1, "missing.png")
    await settle()
    expect(imageSnapshot(1, "missing.png")).toBe("blob:photo")
  })
})
