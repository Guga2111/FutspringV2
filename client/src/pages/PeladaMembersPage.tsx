import { MembersGrid } from "@/components/pelada/MembersGrid"
import { usePeladaWorkspace } from "@/components/pelada/hooks/usePeladaWorkspace"

export default function PeladaMembersPage() {
  const w = usePeladaWorkspace()
  return (
    <MembersGrid
      members={w.pelada.members}
      ranking={w.ranking}
      creatorId={w.pelada.creatorId}
      currentUserId={w.currentUserId}
      maxMensalistas={w.pelada.maxMensalistas}
      isCurrentUserAdmin={w.isAdmin}
      togglingAdmin={w.togglingAdmin}
      togglingMensalista={w.togglingMensalista}
      settingStars={w.settingStars}
      removingMember={w.removingMember}
      bulkPending={w.bulkPending}
      onInvitePlayer={w.openInvite}
      onToggleAdmin={w.toggleAdmin}
      onRemoveMember={w.confirmRemove}
      onSetStars={w.openStars}
      onSetMensalista={w.setMemberMensalista}
      onBulkAction={w.runBulkAction}
    />
  )
}
