import { lazy, Suspense, useState } from "react"
import { Outlet, useLocation, useParams } from "react-router-dom"
import type { PeladaMember } from "@/types/pelada"
import type { BulkMemberAction } from "@/utils/bulkMemberActions"
import { useAuth } from "@/hooks/useAuth"
import { usePeladaDetail } from "@/components/pelada/hooks/usePeladaDetail"
import { usePeladaActions } from "@/components/pelada/hooks/usePeladaActions"
import type { PeladaWorkspace } from "@/components/pelada/hooks/usePeladaWorkspace"
import { PeladaChat } from "@/components/pelada/PeladaChat"
import { PeladaPageHeader } from "@/components/pelada/PeladaPageHeader"
import { DetailSkeleton } from "@/components/pelada/DetailSkeleton"
import { InvitePlayerDialog } from "@/components/pelada/InvitePlayerDialog"
import { ConfirmRemoveDialog } from "@/components/pelada/ConfirmRemoveDialog"
import { CreateSessionDialog } from "@/components/pelada/CreateSessionDialog"
import { SetPlayerStarsDialog } from "@/components/pelada/SetPlayerStarsDialog"
import { areaFromPath, PELADA_AREAS } from "@/utils/peladaAreas"

// Lazy so recharts only downloads when the dialog is first opened
const PlayerHistoryDialog = lazy(() =>
  import("@/components/pelada/PlayerHistoryDialog").then((m) => ({
    default: m.PlayerHistoryDialog,
  })),
)

// Layout of /pelada/:id and its area pages: loads the pelada once, owns the header, the shared dialogs and the chat
export default function PeladaDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { pathname } = useLocation()
  const area = areaFromPath(pathname) ?? "overview"
  const { user: currentUser } = useAuth()

  const detail = usePeladaDetail(id)
  const { pelada, loading, accessDenied, refetchDailies, mergeDaily, mergeMembers, dropMembers } = detail
  const actions = usePeladaActions(pelada, refetchDailies, mergeDaily, mergeMembers, dropMembers)

  const [invitationVersion, setInvitationVersion] = useState(0)
  const [showInvitePlayer, setShowInvitePlayer] = useState(false)
  const [confirmRemoveMember, setConfirmRemoveMember] = useState<PeladaMember | null>(null)
  const [starsMember, setStarsMember] = useState<PeladaMember | null>(null)
  const [showCreateSession, setShowCreateSession] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [historyMounted, setHistoryMounted] = useState(false)
  const [historyUserId, setHistoryUserId] = useState<number | null>(null)

  const openHistory = (userId: number | null) => {
    setHistoryUserId(userId)
    setHistoryMounted(true)
    setHistoryOpen(true)
  }

  const isAdmin = pelada?.members.find((m) => m.id === currentUser?.id)?.isAdmin ?? false

  const runBulkAction = (action: BulkMemberAction, targets: PeladaMember[], stars?: number) => {
    const run: Record<BulkMemberAction, () => Promise<boolean>> = {
      makeAdmin: () => actions.bulkSetAdmin(targets, true),
      removeAdmin: () => actions.bulkSetAdmin(targets, false),
      makeMensalista: () => actions.bulkSetMensalista(targets, true),
      makeDiarista: () => actions.bulkSetMensalista(targets, false),
      setStars: () => (stars === undefined ? Promise.resolve(false) : actions.bulkSetStars(targets, stars)),
      remove: () => actions.bulkRemove(targets),
    }
    return run[action]()
  }

  // Close the dialog before the request, so the member leaves the list with no overlay open
  const handleRemoveConfirm = () => {
    if (!confirmRemoveMember) return
    setConfirmRemoveMember(null)
    void actions.removeMember(confirmRemoveMember)
  }

  let content
  if (loading) {
    content = <DetailSkeleton />
  } else if (accessDenied) {
    content = (
      <div className="flex flex-col items-center justify-center py-24 text-center">
        <h2 className="mb-2 text-xl font-semibold">Acesso negado</h2>
        <p className="text-muted-foreground">Você não faz parte desta pelada.</p>
      </div>
    )
  } else if (!pelada) {
    content = (
      <div className="flex items-center justify-center py-24">
        <p className="text-muted-foreground">Pelada não encontrada.</p>
      </div>
    )
  } else {
    const workspace: PeladaWorkspace = {
      ...detail,
      ...actions,
      pelada,
      isAdmin,
      currentUserId: currentUser?.id ?? null,
      invitationVersion,
      openInvite: () => setShowInvitePlayer(true),
      openCreateSession: () => setShowCreateSession(true),
      confirmRemove: setConfirmRemoveMember,
      openStars: setStarsMember,
      openHistory,
      runBulkAction,
    }
    content = (
      <main className="flex min-w-0 flex-col gap-5 p-4 md:gap-6 md:p-6">
        <PeladaPageHeader
          peladaId={pelada.id}
          peladaName={pelada.name}
          area={area}
          isAdmin={isAdmin}
          title={area === "overview" ? undefined : PELADA_AREAS[area].label}
        />
        <Suspense fallback={<DetailSkeleton />}>
          <Outlet context={workspace} />
        </Suspense>

        {/* hidden where it could cover invitation or finance controls */}
        {area !== "invitations" && area !== "finance" && (
          <PeladaChat
            peladaId={pelada.id}
            peladaName={pelada.name}
            memberCount={pelada.members.length}
            currentUserId={currentUser?.id ?? null}
          />
        )}
      </main>
    )
  }

  return (
    <div className="page-enter flex flex-1 flex-col">
      {content}

      {pelada && historyMounted && (
        <Suspense fallback={null}>
          <PlayerHistoryDialog
            open={historyOpen}
            onOpenChange={setHistoryOpen}
            peladaId={pelada.id}
            members={pelada.members}
            initialUserId={historyUserId}
          />
        </Suspense>
      )}

      {showInvitePlayer && pelada && (
        <InvitePlayerDialog
          peladaId={pelada.id}
          existingMemberIds={new Set(pelada.members.map((m) => m.id))}
          onClose={() => setShowInvitePlayer(false)}
          onInvited={() => setInvitationVersion((v) => v + 1)}
        />
      )}

      {confirmRemoveMember && (
        <ConfirmRemoveDialog
          member={confirmRemoveMember}
          onConfirm={handleRemoveConfirm}
          onClose={() => setConfirmRemoveMember(null)}
        />
      )}

      {starsMember && (
        <SetPlayerStarsDialog
          description={`Nível de ${starsMember.username} nesta pelada, usado para equilibrar os times no sorteio.`}
          defaultStars={starsMember.stars}
          requireChange
          onClose={() => setStarsMember(null)}
          onSave={(stars) => actions.setMemberStars(starsMember, stars)}
        />
      )}

      {showCreateSession && pelada && (
        <CreateSessionDialog
          peladaId={pelada.id}
          defaultTime={pelada.timeOfDay}
          onClose={() => setShowCreateSession(false)}
          onCreated={refetchDailies}
        />
      )}
    </div>
  )
}
