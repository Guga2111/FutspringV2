import { useState, useEffect } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "../ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../ui/select";
import { Skeleton } from "../ui/skeleton";
import { getUser, getUserStats } from "@/api/users";
import {
  ComparablePlayerStatsCard,
  type PlayerCompareData,
} from "./ComparablePlayerStatsCard";
import type { PeladaMember } from "@/types/pelada";

interface ComparePlayersDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  members: PeladaMember[];
  getFileUrl: (path: string | null | undefined) => string | undefined;
}

export function ComparePlayersDialog({
  open,
  onOpenChange,
  members,
  getFileUrl,
}: ComparePlayersDialogProps) {
  const [playerAId, setPlayerAId] = useState<number | null>(null);
  const [playerBId, setPlayerBId] = useState<number | null>(null);
  const [playerAData, setPlayerAData] = useState<PlayerCompareData | null>(null);
  const [playerBData, setPlayerBData] = useState<PlayerCompareData | null>(null);
  const [loading, setLoading] = useState(false);

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setPlayerAId(null);
      setPlayerBId(null);
      setPlayerAData(null);
      setPlayerBData(null);
    }
  }, [open]);

  // Fetch both players when both are selected
  useEffect(() => {
    if (playerAId === null || playerBId === null) return;

    let cancelled = false;
    setLoading(true);
    setPlayerAData(null);
    setPlayerBData(null);

    Promise.all([
      getUser(playerAId),
      getUserStats(playerAId),
      getUser(playerBId),
      getUserStats(playerBId),
    ])
      .then(([profileA, statsA, profileB, statsB]) => {
        if (cancelled) return;
        setPlayerAData({ profile: profileA, stats: statsA });
        setPlayerBData({ profile: profileB, stats: statsB });
      })
      .catch(() => {
        // silently fail — user can reselect
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [playerAId, playerBId]);

  const membersForA = members.filter((m) => m.id !== playerBId);
  const membersForB = members.filter((m) => m.id !== playerAId);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl bg-zinc-950 border-zinc-800 text-white">
        <DialogHeader>
          <DialogTitle className="text-white">Comparar Jogadores</DialogTitle>
        </DialogHeader>

        {/* Player selectors */}
        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-1.5">
            <span className="text-xs text-zinc-400 font-medium uppercase tracking-wide">
              Jogador A
            </span>
            <Select
              value={playerAId?.toString() ?? ""}
              onValueChange={(v) => setPlayerAId(Number(v))}
            >
              <SelectTrigger className="bg-zinc-900 border-zinc-700 text-white">
                <SelectValue placeholder="Selecionar..." />
              </SelectTrigger>
              <SelectContent className="bg-zinc-900 border-zinc-700 text-white">
                {membersForA.map((m) => (
                  <SelectItem
                    key={m.id}
                    value={m.id.toString()}
                    className="text-white focus:bg-zinc-800 focus:text-white"
                  >
                    {m.username}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-1.5">
            <span className="text-xs text-zinc-400 font-medium uppercase tracking-wide">
              Jogador B
            </span>
            <Select
              value={playerBId?.toString() ?? ""}
              onValueChange={(v) => setPlayerBId(Number(v))}
            >
              <SelectTrigger className="bg-zinc-900 border-zinc-700 text-white">
                <SelectValue placeholder="Selecionar..." />
              </SelectTrigger>
              <SelectContent className="bg-zinc-900 border-zinc-700 text-white">
                {membersForB.map((m) => (
                  <SelectItem
                    key={m.id}
                    value={m.id.toString()}
                    className="text-white focus:bg-zinc-800 focus:text-white"
                  >
                    {m.username}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        {/* Comparison area */}
        {(playerAId !== null || playerBId !== null) && (
          <div className="mt-2">
            {loading ? (
              <div className="rounded-xl bg-zinc-900 border border-zinc-800 p-4 space-y-4">
                <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4">
                  <div className="flex flex-col items-center gap-2">
                    <Skeleton className="h-16 w-16 rounded-full bg-zinc-800" />
                    <Skeleton className="h-4 w-24 bg-zinc-800" />
                    <Skeleton className="h-3 w-16 bg-zinc-800" />
                  </div>
                  <span className="font-mono text-xl font-black text-zinc-700">VS</span>
                  <div className="flex flex-col items-center gap-2">
                    <Skeleton className="h-16 w-16 rounded-full bg-zinc-800" />
                    <Skeleton className="h-4 w-24 bg-zinc-800" />
                    <Skeleton className="h-3 w-16 bg-zinc-800" />
                  </div>
                </div>
                <div className="border-t border-zinc-800" />
                <div className="space-y-2">
                  {Array.from({ length: 7 }).map((_, i) => (
                    <Skeleton key={i} className="h-6 w-full bg-zinc-800" />
                  ))}
                </div>
              </div>
            ) : playerAData && playerBData ? (
              <ComparablePlayerStatsCard
                playerA={playerAData}
                playerB={playerBData}
                getFileUrl={getFileUrl}
              />
            ) : null}
          </div>
        )}

        {playerAId === null && playerBId === null && (
          <p className="text-center text-sm text-zinc-500 py-4">
            Selecione dois jogadores para comparar.
          </p>
        )}
      </DialogContent>
    </Dialog>
  );
}
