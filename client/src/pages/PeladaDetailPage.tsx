import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { lazy, Suspense, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import type { PeladaMember } from "@/types/pelada";
import type { BulkMemberAction } from "@/utils/bulkMemberActions";
import { useAuth } from "@/hooks/useAuth";
import { usePeladaDetail } from "@/components/pelada/hooks/usePeladaDetail";
import { usePeladaActions } from "@/components/pelada/hooks/usePeladaActions";
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from "@/components/ui/tabs";
import { NextSessionCard } from "@/components/pelada/NextSessionCard";
import { SessionHistoryList } from "@/components/pelada/SessionHistoryList";
import { PeladaChat } from "@/components/pelada/PeladaChat";
import { pickNextSession } from "@/utils/sessions";
import { MembersGrid } from "@/components/pelada/MembersGrid";
import { RankingTable, type RankingCol } from "@/components/pelada/RankingTable";
import { AwardsTab } from "@/components/pelada/AwardsTab";
import { PeladaBanner } from "@/components/pelada/PeladaBanner";
import { DetailSkeleton } from "@/components/pelada/DetailSkeleton";
import { PeladaInvitations } from "@/components/invitations/PeladaInvitations";
import { InvitePlayerDialog } from "@/components/pelada/InvitePlayerDialog";
import { ConfirmRemoveDialog } from "@/components/pelada/ConfirmRemoveDialog";
import { CreateSessionDialog } from "@/components/pelada/CreateSessionDialog";
import { SetPlayerStarsDialog } from "@/components/pelada/SetPlayerStarsDialog";
import { RankingCommandButton } from "@/components/pelada/RankingCommandButton";

const FinanceTab = lazy(() => import("@/components/finance/FinanceTab").then(m => ({ default: m.FinanceTab })))

// Lazy so recharts only downloads when the dialog is first opened
const PlayerHistoryDialog = lazy(() =>
  import("@/components/pelada/PlayerHistoryDialog").then((m) => ({
    default: m.PlayerHistoryDialog,
  })),
);

export default function PeladaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user: currentUser } = useAuth();

  const {
    pelada,
    dailies,
    ranking,
    awards,
    loading,
    rankingLoading,
    dailiesLoading,
    awardsLoading,
    accessDenied,
    refetchDailies,
    mergeDaily,
    mergeMembers,
    dropMembers,
  } = usePeladaDetail(id);

  const [activeTab, setActiveTab] = useState("members");
  const [invitationVersion, setInvitationVersion] = useState(0);
  const [showInvitePlayer, setShowInvitePlayer] = useState(false);
  const [confirmRemoveMember, setConfirmRemoveMember] =
    useState<PeladaMember | null>(null);
  const [starsMember, setStarsMember] = useState<PeladaMember | null>(null);
  const [showCreateSession, setShowCreateSession] = useState(false);
  const [rankingSort, setRankingSort] = useState<{ col: RankingCol; dir: "asc" | "desc" }>({
    col: "goals",
    dir: "desc",
  });
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyMounted, setHistoryMounted] = useState(false);
  const [historyUserId, setHistoryUserId] = useState<number | null>(null);

  const openPlayerHistory = (userId: number | null) => {
    setHistoryUserId(userId);
    setHistoryMounted(true);
    setHistoryOpen(true);
  };

  const handleRankingSort = (col: RankingCol) => {
    setRankingSort((prev) =>
      prev.col === col
        ? { col, dir: prev.dir === "desc" ? "asc" : "desc" }
        : { col, dir: "desc" },
    );
  };

  const sortedRanking = useMemo(
    () =>
      [...ranking].sort((a, b) => {
        const diff = b[rankingSort.col] - a[rankingSort.col];
        return rankingSort.dir === "desc" ? diff : -diff;
      }),
    [ranking, rankingSort],
  );

  const isCurrentUserAdmin =
    pelada?.members.find((m) => m.id === currentUser?.id)?.isAdmin ?? false;

  const {
    removingMember,
    settingStars,
    togglingAdmin,
    togglingMensalista,
    bulkPending,
    attendancePending,
    removeMember,
    toggleAdmin,
    setMemberStars,
    setMemberMensalista,
    bulkSetAdmin,
    bulkSetMensalista,
    bulkSetStars,
    bulkRemove,
    toggleAttendance,
  } = usePeladaActions(pelada, refetchDailies, mergeDaily, mergeMembers, dropMembers);

  const handleBulkAction = (action: BulkMemberAction, targets: PeladaMember[], stars?: number) => {
    const run: Record<BulkMemberAction, () => Promise<boolean>> = {
      makeAdmin: () => bulkSetAdmin(targets, true),
      removeAdmin: () => bulkSetAdmin(targets, false),
      makeMensalista: () => bulkSetMensalista(targets, true),
      makeDiarista: () => bulkSetMensalista(targets, false),
      setStars: () => (stars === undefined ? Promise.resolve(false) : bulkSetStars(targets, stars)),
      remove: () => bulkRemove(targets),
    };
    return run[action]();
  };

  const nextSession = useMemo(() => pickNextSession(dailies), [dailies]);
  const pastSessions = useMemo(
    () => dailies.filter((d) => d.id !== nextSession?.id),
    [dailies, nextSession],
  );

  // Close the dialog before the request, so the member leaves the list with no overlay open
  const handleRemoveConfirm = () => {
    if (!confirmRemoveMember) return;
    setConfirmRemoveMember(null);
    void removeMember(confirmRemoveMember);
  };

  return (
    <div className="page-enter flex flex-1 flex-col">
      {loading ? (
        <DetailSkeleton />
      ) : accessDenied ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <span className="text-5xl mb-4">🚫</span>
          <h2 className="text-xl font-semibold mb-2">Acesso negado</h2>
          <p className="text-muted-foreground">
            Você não faz parte desta pelada.
          </p>
        </div>
      ) : pelada ? (
        <main className={activeTab === "finance" ? "flex flex-col gap-3 p-4 md:gap-6 md:p-6" : "flex flex-col gap-5 p-4 md:gap-6 md:p-6"}>
          {activeTab === "finance" ? (
            <header className="flex items-center gap-3 py-1">
              <h1 className="text-xl font-semibold">{pelada.name}</h1>
            </header>
          ) : <PeladaBanner pelada={pelada} isCurrentUserAdmin={isCurrentUserAdmin} />}

          <div className="min-w-0">
              <Tabs value={activeTab} onValueChange={setActiveTab} className="flex flex-col gap-3 md:gap-5">
                {activeTab === "finance" && <Select value={activeTab} onValueChange={setActiveTab}><SelectTrigger aria-label="Área da pelada" className="md:hidden"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="members">Membros</SelectItem><SelectItem value="sessions">Sessões</SelectItem><SelectItem value="ranking">Ranking</SelectItem><SelectItem value="awards">Prêmios</SelectItem><SelectItem value="finance">Financeiro</SelectItem>{isCurrentUserAdmin && <SelectItem value="invitations">Convites</SelectItem>}</SelectGroup></SelectContent></Select>}
                <TabsList variant="pill" className={activeTab === "finance" ? "hidden h-auto max-w-full self-start flex-wrap md:flex" : "h-auto max-w-full self-start flex-wrap max-md:grid max-md:grid-cols-3 max-md:rounded-tile"}>
                  <TabsTrigger variant="pill" value="members">Membros</TabsTrigger>
                  <TabsTrigger variant="pill" value="sessions">Sessões</TabsTrigger>
                  <TabsTrigger variant="pill" value="ranking">Ranking</TabsTrigger>
                  <TabsTrigger variant="pill" value="awards">Prêmios</TabsTrigger>
                  <TabsTrigger variant="pill" value="finance">Financeiro</TabsTrigger>
                  {isCurrentUserAdmin && <TabsTrigger variant="pill" value="invitations">Convites</TabsTrigger>}
                </TabsList>

                <TabsContent value="members" className="mt-0">
                  <MembersGrid
                    members={pelada.members}
                    ranking={ranking}
                    creatorId={pelada.creatorId}
                    currentUserId={currentUser?.id ?? null}
                    maxMensalistas={pelada.maxMensalistas}
                    isCurrentUserAdmin={isCurrentUserAdmin}
                    togglingAdmin={togglingAdmin}
                    togglingMensalista={togglingMensalista}
                    settingStars={settingStars}
                    removingMember={removingMember}
                    bulkPending={bulkPending}
                    onInvitePlayer={() => setShowInvitePlayer(true)}
                    onToggleAdmin={toggleAdmin}
                    onRemoveMember={setConfirmRemoveMember}
                    onSetStars={setStarsMember}
                    onSetMensalista={setMemberMensalista}
                    onBulkAction={handleBulkAction}
                  />
                </TabsContent>

                <TabsContent value="sessions" className="mt-0 flex flex-col gap-5">
                  {!dailiesLoading && (
                    <NextSessionCard
                      daily={nextSession}
                      timeZone={pelada.timeZone}
                      isAdmin={isCurrentUserAdmin}
                      attendancePending={attendancePending}
                      onToggleAttendance={toggleAttendance}
                    />
                  )}
                  <SessionHistoryList
                    dailies={pastSessions}
                    isLoading={dailiesLoading}
                    isAdmin={isCurrentUserAdmin}
                    onOpenCreate={() => setShowCreateSession(true)}
                  />
                </TabsContent>

                <TabsContent value="ranking" className="mt-0 flex flex-col gap-3">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <span className="text-sm text-muted-foreground">
                      Top jogadores · {sortedRanking.length} classificados
                    </span>
                    <RankingCommandButton
                      peladaId={pelada.id}
                      members={pelada.members}
                      creatorId={pelada.creatorId}
                      isAdmin={isCurrentUserAdmin}
                      onCreateSession={() => setShowCreateSession(true)}
                      onInvitePlayer={() => setShowInvitePlayer(true)}
                      onRemovePlayer={setConfirmRemoveMember}
                      onOpenHistory={() => openPlayerHistory(null)}
                    />
                  </div>
                  <RankingTable
                    ranking={sortedRanking}
                    isLoading={rankingLoading}
                    sortConfig={rankingSort}
                    onSort={handleRankingSort}
                    onOpenHistory={openPlayerHistory}
                  />
                </TabsContent>

                <TabsContent value="finance" className="mt-0">{activeTab === "finance" && <Suspense fallback={<DetailSkeleton />}><FinanceTab key={pelada.id} peladaId={pelada.id} userId={currentUser?.id ?? null} members={pelada.members} dailies={dailies} /></Suspense>}</TabsContent>

                {isCurrentUserAdmin && <TabsContent value="invitations" className="mt-0"><PeladaInvitations peladaId={pelada.id} version={invitationVersion} onInvite={() => setShowInvitePlayer(true)} /></TabsContent>}

                <TabsContent value="awards" className="mt-0">
                  <AwardsTab awards={awards} isLoading={awardsLoading} />
                </TabsContent>
              </Tabs>
          </div>

          {activeTab !== "invitations" && activeTab !== "finance" && <PeladaChat
            peladaId={pelada.id}
            peladaName={pelada.name}
            memberCount={pelada.members.length}
            currentUserId={currentUser?.id ?? null}
          />}
        </main>
      ) : (
        <div className="flex items-center justify-center py-24">
          <p className="text-muted-foreground">Pelada não encontrada.</p>
        </div>
      )}

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
          onInvited={() => setInvitationVersion(v => v + 1)}
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
          onSave={(stars) => setMemberStars(starsMember, stars)}
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
  );
}
