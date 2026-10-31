import apiClient from "@/api/client"
import { invitePlayerSchema, type InvitePlayerValues } from "@/schemas/invitation"
import type { Invitation, InviteLink, JoinPelada, PeladaPreview } from "@/types/invitation"

export const INVITATIONS_PAGE_SIZE = 20
export async function invitePlayer(peladaId: number, request: InvitePlayerValues): Promise<Invitation> {
  return (await apiClient.post<Invitation>(`/api/v1/peladas/${peladaId}/invitations`, invitePlayerSchema.parse(request))).data
}
export async function getPeladaInvitations(peladaId: number, pending: boolean, page = 0): Promise<Invitation[]> {
  return (await apiClient.get<Invitation[]>(`/api/v1/peladas/${peladaId}/invitations`, { params: { pending, page, size: INVITATIONS_PAGE_SIZE } })).data
}
export async function cancelInvitation(peladaId: number, id: number): Promise<void> {
  await apiClient.post(`/api/v1/peladas/${peladaId}/invitations/${id}/cancel`)
}
export async function getMyInvitations(page = 0): Promise<Invitation[]> {
  return (await apiClient.get<Invitation[]>("/api/v1/invitations", { params: { page, size: INVITATIONS_PAGE_SIZE } })).data
}
export async function getInvitation(id: number): Promise<Invitation> {
  return (await apiClient.get<Invitation>(`/api/v1/invitations/${id}`)).data
}
export async function acceptInvitation(id: number): Promise<JoinPelada> {
  return (await apiClient.post<JoinPelada>(`/api/v1/invitations/${id}/accept`)).data
}
export async function declineInvitation(id: number): Promise<void> {
  await apiClient.post(`/api/v1/invitations/${id}/decline`)
}
export async function getInviteLink(peladaId: number): Promise<InviteLink | null> {
  const response = await apiClient.get<InviteLink>(`/api/v1/peladas/${peladaId}/invite-link`)
  return response.status === 204 ? null : response.data
}
export async function replaceInviteLink(peladaId: number): Promise<InviteLink> {
  return (await apiClient.post<InviteLink>(`/api/v1/peladas/${peladaId}/invite-link`)).data
}
export async function revokeInviteLink(peladaId: number): Promise<void> {
  await apiClient.delete(`/api/v1/peladas/${peladaId}/invite-link`)
}
export async function getInvitePreview(token: string): Promise<PeladaPreview> {
  return (await apiClient.get<PeladaPreview>(`/api/v1/invite-links/${encodeURIComponent(token)}`)).data
}
export async function joinInviteLink(token: string): Promise<JoinPelada> {
  return (await apiClient.post<JoinPelada>(`/api/v1/invite-links/${encodeURIComponent(token)}/join`)).data
}
export function inviteLinkUrl(token: string): string { return `${window.location.origin}/join/${encodeURIComponent(token)}` }
