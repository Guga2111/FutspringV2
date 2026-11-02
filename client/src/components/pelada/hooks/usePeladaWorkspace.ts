import { useOutletContext } from "react-router-dom"
import type { usePeladaActions } from "@/components/pelada/hooks/usePeladaActions"
import type { usePeladaDetail } from "@/components/pelada/hooks/usePeladaDetail"
import type { PeladaDetail, PeladaMember } from "@/types/pelada"
import type { BulkMemberAction } from "@/utils/bulkMemberActions"

// What the pelada layout (PeladaDetailPage) shares with the area pages through <Outlet context>.
// The area pages only render once the pelada has loaded, so `pelada` is never null here.
export type PeladaWorkspace = Omit<ReturnType<typeof usePeladaDetail>, "pelada"> &
  ReturnType<typeof usePeladaActions> & {
    pelada: PeladaDetail
    isAdmin: boolean
    currentUserId: number | null
    // goes up after an invitation is sent, so the Convites page reloads its list
    invitationVersion: number
    openInvite: () => void
    openCreateSession: () => void
    confirmRemove: (member: PeladaMember) => void
    openStars: (member: PeladaMember) => void
    openHistory: (userId: number | null) => void
    runBulkAction: (action: BulkMemberAction, targets: PeladaMember[], stars?: number) => Promise<boolean>
  }

export function usePeladaWorkspace(): PeladaWorkspace {
  return useOutletContext<PeladaWorkspace>()
}
