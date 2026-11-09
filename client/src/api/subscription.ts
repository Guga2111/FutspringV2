import apiClient from "@/api/client"
import type { PixClaimValues } from "@/schemas/subscription"
import type { PeladaSubscription } from "@/types/subscription"

// The pelada's subscription as its creator sees it (anyone else gets 403)
export async function getPeladaSubscription(peladaId: number): Promise<PeladaSubscription> {
  return (await apiClient.get<PeladaSubscription>(`/api/v1/peladas/${peladaId}/subscription`)).data
}

// "Já fiz o Pix": idempotent, another plan only changes the plan informed
export async function claimPix(peladaId: number, body: PixClaimValues): Promise<PeladaSubscription> {
  return (await apiClient.post<PeladaSubscription>(`/api/v1/peladas/${peladaId}/subscription/pix-claim`, body)).data
}
