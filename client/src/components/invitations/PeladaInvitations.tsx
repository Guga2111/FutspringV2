import { InvitationsCard } from "@/components/invitations/InvitationsCard"
import { InviteLinkCard } from "@/components/invitations/InviteLinkCard"

// Convites area of a pelada (admins): the shareable link and the individual invitations
export function PeladaInvitations({ peladaId, peladaName, version, onInvite }: { peladaId: number; peladaName: string; version: number; onInvite: () => void }) {
  return (
    <div className="flex min-w-0 max-w-4xl flex-col gap-5">
      <InviteLinkCard peladaId={peladaId} peladaName={peladaName} />
      <InvitationsCard peladaId={peladaId} version={version} onInvite={onInvite} />
    </div>
  )
}
