import { beforeEach, describe, expect, it, vi } from "vitest"
vi.mock("@/api/client", () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))
import api from "@/api/client"
import { acceptInvitation, cancelInvitation, declineInvitation, getInvitation, getInviteLink, getInvitePreview, getMyInvitations, getPeladaInvitations, invitePlayer, joinInviteLink, replaceInviteLink, revokeInviteLink } from "@/api/invitations"

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.post).mockResolvedValue({ data: { peladaId: 7 } })
  vi.mocked(api.get).mockResolvedValue({ status: 200, data: [] })
  vi.mocked(api.delete).mockResolvedValue({})
})
describe("consent-based membership contract", () => {
  it("creates an invitation rather than adding a player", async () => {
    await invitePlayer(7, { userId: 8 })
    expect(api.post).toHaveBeenCalledWith("/api/v1/peladas/7/invitations", { userId: 8 })
    await expect(invitePlayer(7, { userId: 0 })).rejects.toThrow()
    expect(api.post).toHaveBeenCalledTimes(1)
  })
  it("loads paginated inbox and separate pending/history pages", async () => {
    await getMyInvitations(2)
    expect(api.get).toHaveBeenLastCalledWith("/api/v1/invitations", { params: { page: 2, size: 20 } })
    await getPeladaInvitations(7, false, 3)
    expect(api.get).toHaveBeenLastCalledWith("/api/v1/peladas/7/invitations", { params: { pending: false, page: 3, size: 20 } })
  })
  it("reads without responding and only explicit POST accepts/declines", async () => {
    await getInvitation(3)
    expect(api.get).toHaveBeenLastCalledWith("/api/v1/invitations/3")
    expect(api.post).not.toHaveBeenCalled()
    await expect(acceptInvitation(3)).resolves.toEqual({ peladaId: 7 })
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/invitations/3/accept")
    await declineInvitation(3)
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/invitations/3/decline")
    await cancelInvitation(7, 3)
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/peladas/7/invitations/3/cancel")
  })
  it("separates public preview from authenticated joining", async () => {
    await getInvitePreview("a.b")
    expect(api.get).toHaveBeenLastCalledWith("/api/v1/invite-links/a.b")
    expect(api.post).not.toHaveBeenCalled()
    await joinInviteLink("a.b")
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/invite-links/a.b/join")
  })
  it("handles absence of a link and uses explicit replacement/revocation", async () => {
    vi.mocked(api.get).mockResolvedValue({ status: 204, data: "" })
    await expect(getInviteLink(7)).resolves.toBeNull()
    await replaceInviteLink(7)
    expect(api.post).toHaveBeenLastCalledWith("/api/v1/peladas/7/invite-link")
    await revokeInviteLink(7)
    expect(api.delete).toHaveBeenLastCalledWith("/api/v1/peladas/7/invite-link")
  })
})
