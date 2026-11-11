import { lazy, Suspense, useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { PlusSignIcon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { PageContainer } from "@/components/layout/PageContainer"
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
import { InvitePlayerDialog } from "@/components/pelada/members/InvitePlayerDialog"
import { ConfirmRemoveDialog } from "@/components/pelada/members/ConfirmRemoveDialog"
import { CreateSessionDialog } from "@/components/pelada/sessions/CreateSessionDialog"
import { SetPlayerStarsDialog } from "@/components/pelada/members/SetPlayerStarsDialog"
import { SUBSCRIPTION_NOTICE_ID, SubscriptionNotice } from "@/components/pelada/SubscriptionNotice"
import { isWritable } from "@/utils/subscription"
import { areaFromPath, PELADA_AREAS, peladaRole } from "@/utils/peladaAreas"
import { cn } from "@/lib/utils"

// Lazy so recharts only downloads when the dialog is first opened
const PlayerHistoryDialog = lazy(() =>
  import("@/components/pelada/ranking/PlayerHistoryDialog").then((m) => ({
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
  const isCreator = pelada != null && currentUser != null && pelada.creatorId === currentUser.id
  const role = peladaRole(isAdmin, isCreator)
  const writable = isWritable(pelada?.subscription)

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
      isCreator,
      role,
      writable,
      currentUserId: currentUser?.id ?? null,
      invitationVersion,
      openInvite: () => setShowInvitePlayer(true),
      openCreateSession: () => setShowCreateSession(true),
      confirmRemove: setConfirmRemoveMember,
      openStars: setStarsMember,
      openHistory,
      runBulkAction,
    }
    // the area's main action sits in the header, at the right of the title (admins only)
    const headerAction =
      isAdmin && area === "members" ? (
        <Button variant="brand" onClick={() => setShowInvitePlayer(true)} className="h-9 gap-1.5 px-4 font-semibold">
          <HugeiconsIcon icon={PlusSignIcon} className="size-[15px]" strokeWidth={2.2} />
          Convidar jogador
        </Button>
      ) : isAdmin && area === "sessions" ? (
        <Button
          variant="brand"
          disabled={!writable}
          aria-describedby={writable ? undefined : SUBSCRIPTION_NOTICE_ID}
          onClick={() => setShowCreateSession(true)}
          className="h-9 gap-1.5 px-4 font-semibold"
        >
          <HugeiconsIcon icon={PlusSignIcon} className="size-[15px]" strokeWidth={2.2} />
          Nova sessão
        </Button>
      ) : null
    // the chat button is fixed in the corner: leave room under the content so it never covers the last row
    const showChat = area !== "invitations" && area !== "finance" && area !== "subscription"
    content = (
      <PageContainer className={cn(showChat && "pb-24 md:pb-24")}>
        <PeladaPageHeader
          peladaId={pelada.id}
          peladaName={pelada.name}
          area={area}
          role={role}
          title={area === "overview" ? undefined : PELADA_AREAS[area].label}
          actions={headerAction}
        />
        {/* the Assinatura area shows the state itself */}
        {area !== "subscription" && (
          <SubscriptionNotice
            peladaId={pelada.id}
            subscription={pelada.subscription}
            isCreator={isCreator}
            timeZone={pelada.timeZone}
          />
        )}
        <Suspense fallback={<DetailSkeleton />}>
          <Outlet context={workspace} />
        </Suspense>

        {/* hidden where it could cover invitation or finance controls */}
        {showChat && (
          <PeladaChat
            peladaId={pelada.id}
            peladaName={pelada.name}
            memberCount={pelada.members.length}
            currentUserId={currentUser?.id ?? null}
          />
        )}
      </PageContainer>
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
