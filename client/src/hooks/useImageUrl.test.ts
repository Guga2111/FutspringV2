import { describe, expect, it, vi } from "vitest"
vi.mock("@/api/client", () => ({ default: { get: vi.fn().mockResolvedValue({ data: new Blob() }) } }))
import client from "@/api/client"
import { getImage } from "@/api/files"
describe("private photo requests", () => {
  it("loads bytes through the authenticated API client and encodes the filename", async () => {
    const blob = await getImage("photo name.jpg")
    expect(blob).toBeInstanceOf(Blob)
    expect(client.get).toHaveBeenCalledWith("/api/v1/files/photo%20name.jpg", { responseType: "blob" })
  })
})
