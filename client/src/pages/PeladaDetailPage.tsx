import { lazy, Suspense, useMemo, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import type { PeladaMember } from "@/types/pelada";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { usePeladaDetail } from "@/components/pelada/hooks/usePeladaDetail";
import { usePeladaActions } from "@/components/pelada/hooks/usePeladaActions";
import EditPeladaModal from "@/components/EditPeladaModal";
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from "@/components/ui/tabs";
import { MessageCircle } from "lucide-react";
import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { SessionsTable } from "@/components/pelada/SessionTable";
import { MembersGrid } from "@/components/pelada/MembersGrid";
import { RankingTable, type RankingCol } from "@/components/pelada/RankingTable";
import { AwardsTab } from "@/components/pelada/AwardsTab";
import { PeladaBanner } from "@/components/pelada/PeladaBanner";
import { ChatSidebar } from "@/components/pelada/ChatSidebar";
import { DetailSkeleton } from "@/components/pelada/DetailSkeleton";
import { AddPlayerDialog } from "@/components/pelada/AddPlayerDialog";
import { ConfirmRemoveDialog } from "@/components/pelada/ConfirmRemoveDialog";
import { CreateSessionDialog } from "@/components/pelada/CreateSessionDialog";
import { DeletePeladaDialog } from "@/components/pelada/DeletePeladaDialog";
import { RankingCommandButton } from "@/components/pelada/RankingCommandButton";

// Lazy so recharts only downloads when the dialog is first opened
const PlayerHistoryDialog = lazy(() =>
  import("@/components/pelada/PlayerHistoryDialog").then((m) => ({
    default: m.PlayerHistoryDialog,
  })),
);

export default function PeladaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
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
    refetchPelada,
    refetchDailies,
  } = usePeladaDetail(id);

  const [showAddPlayer, setShowAddPlayer] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [confirmRemoveMember, setConfirmRemoveMember] =
    useState<PeladaMember | null>(null);
  const [showCreateSession, setShowCreateSession] = useState(false);
  const [rankingSort, setRankingSort] = useState<{ col: RankingCol; dir: "asc" | "desc" }>({
    col: "goals",
    dir: "desc",
  });
  const [chatCollapsed, setChatCollapsed] = useState(false);
  const [showMobileChat, setShowMobileChat] = useState(false);
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
  const creatorId = pelada?.creatorId ?? null;
  const isCurrentUserCreator =
    currentUser != null && creatorId === currentUser.id;

  const { deleting, removing, togglingAdmin, removePeladaAndLeave, removeMember, toggleAdmin } =
    usePeladaActions(pelada, refetchPelada);

  const handleDelete = async () => {
    if (!(await removePeladaAndLeave())) setShowDeleteConfirm(false);
  };

  const handleRemoveConfirm = async () => {
    if (confirmRemoveMember && (await removeMember(confirmRemoveMember))) {
      setConfirmRemoveMember(null);
    }
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
        <main className="flex flex-col gap-5 p-4 md:gap-6 md:p-6">
          <PeladaBanner
            pelada={pelada}
            isCurrentUserAdmin={isCurrentUserAdmin}
            isCurrentUserCreator={isCurrentUserCreator}
            onEdit={() => setShowEdit(true)}
            onDelete={() => setShowDeleteConfirm(true)}
          />

          <div className="flex gap-6">
            <div className="min-w-0 flex-1">
              <Tabs defaultValue="members" className="flex flex-col gap-5">
                <TabsList variant="pill" className="self-start">
                  <TabsTrigger variant="pill" value="members">Membros</TabsTrigger>
                  <TabsTrigger variant="pill" value="sessions">Sessões</TabsTrigger>
                  <TabsTrigger variant="pill" value="ranking">Ranking</TabsTrigger>
                  <TabsTrigger variant="pill" value="awards">Prêmios</TabsTrigger>
                </TabsList>

                <TabsContent value="members" className="mt-0">
                  <MembersGrid
                    members={pelada.members}
                    ranking={ranking}
                    creatorId={pelada.creatorId}
                    isCurrentUserAdmin={isCurrentUserAdmin}
                    togglingAdmin={togglingAdmin}
                    onAddPlayer={() => setShowAddPlayer(true)}
                    onToggleAdmin={toggleAdmin}
                    onRemoveMember={setConfirmRemoveMember}
                  />
                </TabsContent>

                <TabsContent value="sessions" className="mt-0">
                  <SessionsTable
                    dailies={dailies}
                    isLoading={dailiesLoading}
                    isAdmin={isCurrentUserAdmin}
                    onOpenCreate={() => setShowCreateSession(true)}
                    onNavigate={(id) => navigate(`/daily/${id}`)}
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
                      isAdmin={isCurrentUserAdmin}
                      onCreateSession={() => setShowCreateSession(true)}
                      onAddPlayer={() => setShowAddPlayer(true)}
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

                <TabsContent value="awards" className="mt-0">
                  <AwardsTab awards={awards} isLoading={awardsLoading} />
                </TabsContent>
              </Tabs>
            </div>
            {/* end flex-1 main content */}

            {/* Chat sidebar — desktop only */}
            <div className="hidden lg:block w-80 flex-shrink-0 sticky top-4 self-start h-[calc(100vh-6rem)]">
              <ChatSidebar
                peladaId={pelada.id}
                currentUserId={currentUser?.id ?? null}
                collapsed={chatCollapsed}
                onToggle={() => setChatCollapsed((v) => !v)}
              />
            </div>
          </div>

          {/* Mobile floating chat button */}
          <Button
            variant="gradient"
            size="icon"
            aria-label="Abrir chat"
            className="fixed bottom-6 right-6 z-40 size-12 shadow-lg lg:hidden"
            onClick={() => setShowMobileChat((v) => !v)}
          >
            <MessageCircle className="size-6" />
          </Button>

          {/* Mobile chat drawer */}
          <Drawer open={showMobileChat} onOpenChange={setShowMobileChat}>
            <DrawerContent className="lg:hidden h-[70vh] flex flex-col">
              <DrawerHeader className="sr-only">
                <DrawerTitle>Chat</DrawerTitle>
              </DrawerHeader>
              <div className="flex-1 min-h-0 pt-2">
                <ChatSidebar
                  peladaId={pelada.id}
                  currentUserId={currentUser?.id ?? null}
                  collapsed={false}
                  onToggle={() => setShowMobileChat(false)}
                />
              </div>
            </DrawerContent>
          </Drawer>
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

      {showAddPlayer && pelada && (
        <AddPlayerDialog
          peladaId={pelada.id}
          existingMemberIds={new Set(pelada.members.map((m) => m.id))}
          onClose={() => setShowAddPlayer(false)}
          onAdded={refetchPelada}
        />
      )}

      {confirmRemoveMember && (
        <ConfirmRemoveDialog
          member={confirmRemoveMember}
          onConfirm={handleRemoveConfirm}
          onClose={() => setConfirmRemoveMember(null)}
          loading={removing}
        />
      )}

      {showEdit && pelada && (
        <EditPeladaModal
          pelada={pelada}
          onClose={() => setShowEdit(false)}
          onUpdated={refetchPelada}
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

      {showDeleteConfirm && pelada && (
        <DeletePeladaDialog
          peladaName={pelada.name}
          deleting={deleting}
          onConfirm={handleDelete}
          onClose={() => setShowDeleteConfirm(false)}
        />
      )}
    </div>
  );
}
