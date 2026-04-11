import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import NavBar from "../components/NavBar";
import {
  removePlayer,
  setAdmin,
  deletePelada,
} from "../api/peladas";
import type { PeladaMember } from "../types/pelada";
import { useAuth } from "../hooks/useAuth";
import { getFileUrl } from "../lib/utils";
import { usePeladaDetail } from "../components/pelada/hooks/usePeladaDetail";
import EditPeladaModal from "../components/EditPeladaModal";
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from "../components/ui/tabs";
import { MessageCircle } from "lucide-react";
import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle,
} from "../components/ui/drawer";
import { SessionsTable } from "@/components/pelada/SessionTable";
import { MembersTable } from "@/components/pelada/MembersTable";
import { RankingTable } from "@/components/pelada/RankingTable";
import { AwardsTab } from "@/components/pelada/AwardsTab";
import { PeladaBanner } from "@/components/pelada/PeladaBanner";
import { ChatSidebar } from "@/components/pelada/ChatSidebar";
import { DetailSkeleton } from "@/components/pelada/DetailSkeleton";
import { AddPlayerDialog } from "@/components/pelada/AddPlayerDialog";
import { ConfirmRemoveDialog } from "@/components/pelada/ConfirmRemoveDialog";
import { CreateSessionDialog } from "@/components/pelada/CreateSessionDialog";
import { DeletePeladaDialog } from "@/components/pelada/DeletePeladaDialog";

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
  const [deleting, setDeleting] = useState(false);
  const [confirmRemoveMember, setConfirmRemoveMember] =
    useState<PeladaMember | null>(null);
  const [removing, setRemoving] = useState(false);
  const [togglingAdmin, setTogglingAdmin] = useState<number | null>(null);
  const [showCreateSession, setShowCreateSession] = useState(false);
  const [rankingSort, setRankingSort] = useState<{
    col: "goals" | "assists" | "matchesPlayed" | "wins";
    dir: "asc" | "desc";
  }>({ col: "goals", dir: "desc" });
  const [chatCollapsed, setChatCollapsed] = useState(false);
  const [showMobileChat, setShowMobileChat] = useState(false);

  type RankingCol = "goals" | "assists" | "matchesPlayed" | "wins";

  const handleRankingSort = (col: RankingCol) => {
    setRankingSort((prev) =>
      prev.col === col
        ? { col, dir: prev.dir === "desc" ? "asc" : "desc" }
        : { col, dir: "desc" },
    );
  };

  const sortedRanking = [...ranking].sort((a, b) => {
    const diff = b[rankingSort.col] - a[rankingSort.col];
    return rankingSort.dir === "desc" ? diff : -diff;
  });

  const isCurrentUserAdmin =
    pelada?.members.find((m) => m.id === currentUser?.id)?.isAdmin ?? false;
  const creatorId = pelada?.creatorId ?? null;
  const isCurrentUserCreator =
    currentUser != null && creatorId === currentUser.id;

  const handleDelete = async () => {
    if (!pelada) return;
    setDeleting(true);
    try {
      await deletePelada(pelada.id);
      toast.success("Pelada deleted");
      navigate("/home");
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      toast.error(e?.response?.data?.message ?? "Failed to delete pelada");
      setDeleting(false);
      setShowDeleteConfirm(false);
    }
  };

  const handleRemoveConfirm = async () => {
    if (!confirmRemoveMember || !pelada) return;
    setRemoving(true);
    try {
      await removePlayer(pelada.id, confirmRemoveMember.id);
      toast.success(`${confirmRemoveMember.username} removed from pelada`);
      setConfirmRemoveMember(null);
      refetchPelada();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      toast.error(e?.response?.data?.message ?? "Failed to remove player");
    } finally {
      setRemoving(false);
    }
  };

  const handleToggleAdmin = async (member: PeladaMember) => {
    if (!pelada) return;
    setTogglingAdmin(member.id);
    try {
      await setAdmin(pelada.id, member.id, !member.isAdmin);
      toast.success(
        member.isAdmin
          ? `${member.username} is no longer an admin`
          : `${member.username} is now an admin`,
      );
      refetchPelada();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      toast.error(
        e?.response?.data?.message ?? "Failed to update admin status",
      );
    } finally {
      setTogglingAdmin(null);
    }
  };

  return (
    <div className="page-enter min-h-screen flex flex-col">
      <NavBar />
      {loading ? (
        <DetailSkeleton />
      ) : accessDenied ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <span className="text-5xl mb-4">🚫</span>
          <h2 className="text-xl font-semibold mb-2">Acesso Negado</h2>
          <p className="text-muted-foreground">
            Você não faz parte dessa pelada.
          </p>
        </div>
      ) : pelada ? (
        <main>
          {/* Header image / banner */}
          <PeladaBanner
            pelada={pelada}
            isCurrentUserAdmin={isCurrentUserAdmin}
            isCurrentUserCreator={isCurrentUserCreator}
            onEdit={() => setShowEdit(true)}
            onDelete={() => setShowDeleteConfirm(true)}
            getFileUrl={getFileUrl}
          />

          <div className="container max-w-6xl mx-auto px-4 py-6 flex gap-6">
            <div className="flex-1 min-w-0">
              <Tabs defaultValue="members" className="mt-4 flex flex-col gap-4">
                <TabsList className="rounded-full bg-muted p-1 mx-auto w-fit flex">
                  <TabsTrigger
                    value="members"
                    className="rounded-full data-[state=active]:bg-background data-[state=active]:shadow-sm"
                  >
                    Membros
                  </TabsTrigger>
                  <TabsTrigger
                    value="sessions"
                    className="rounded-full data-[state=active]:bg-background data-[state=active]:shadow-sm"
                  >
                    Sessões
                  </TabsTrigger>
                  <TabsTrigger
                    value="ranking"
                    className="rounded-full data-[state=active]:bg-background data-[state=active]:shadow-sm"
                  >
                    Ranking
                  </TabsTrigger>
                  <TabsTrigger
                    value="awards"
                    className="rounded-full data-[state=active]:bg-background data-[state=active]:shadow-sm"
                  >
                    Prêmios
                  </TabsTrigger>
                </TabsList>

                {/* Members tab */}
                <TabsContent value="members">
                  <MembersTable
                    members={pelada.members}
                    creatorId={pelada.creatorId}
                    isCurrentUserAdmin={isCurrentUserAdmin}
                    togglingAdmin={togglingAdmin}
                    onAddPlayer={() => setShowAddPlayer(true)}
                    onToggleAdmin={handleToggleAdmin}
                    onRemoveMember={setConfirmRemoveMember}
                  />
                </TabsContent>

                {/* Sessions tab */}
                <TabsContent value="sessions">
                  <SessionsTable
                    dailies={dailies}
                    isLoading={dailiesLoading}
                    isAdmin={isCurrentUserAdmin}
                    onOpenCreate={() => setShowCreateSession(true)}
                    onNavigate={(id) => navigate(`/daily/${id}`)}
                  />
                </TabsContent>

                {/* Ranking tab */}
                <TabsContent value="ranking">
                  <RankingTable
                    ranking={sortedRanking}
                    isLoading={rankingLoading}
                    sortConfig={rankingSort}
                    onSort={handleRankingSort}
                    getFileUrl={getFileUrl}
                  />
                </TabsContent>

                {/* Awards tab */}
                <TabsContent value="awards">
                  <AwardsTab awards={awards} isLoading={awardsLoading} />
                </TabsContent>
              </Tabs>
            </div>
            {/* end flex-1 main content */}

            {/* Chat sidebar — desktop only */}
            <div className="hidden lg:block w-80 flex-shrink-0">
              <ChatSidebar
                peladaId={pelada.id}
                currentUserId={currentUser?.id ?? null}
                collapsed={chatCollapsed}
                onToggle={() => setChatCollapsed((v) => !v)}
              />
            </div>
          </div>

          {/* Mobile floating chat button */}
          <button
            aria-label="Open chat"
            className="fixed bottom-6 right-6 z-40 lg:hidden p-3 rounded-full bg-green-600 text-white shadow-lg hover:bg-green-500 transition-colors"
            onClick={() => setShowMobileChat((v) => !v)}
          >
            <MessageCircle className="h-6 w-6" />
          </button>

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
