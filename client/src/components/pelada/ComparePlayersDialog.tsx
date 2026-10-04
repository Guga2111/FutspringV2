import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { ComparablePlayerStatsCard } from "@/components/pelada/ComparablePlayerStatsCard";
import { useComparePlayers } from "@/components/pelada/hooks/useComparePlayers";
import type { PeladaMember } from "@/types/pelada";

interface ComparePlayersDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  peladaId: number;
  members: PeladaMember[];
}

export function ComparePlayersDialog({
  open,
  onOpenChange,
  peladaId,
  members,
}: ComparePlayersDialogProps) {
  const [playerAId, setPlayerAId] = useState<number | null>(null);
  const [playerBId, setPlayerBId] = useState<number | null>(null);
  const { loading, playerA: playerAData, playerB: playerBData } = useComparePlayers(peladaId, playerAId, playerBId);

  // Start empty the next time the dialog opens
  function handleOpenChange(next: boolean) {
    if (!next) {
      setPlayerAId(null);
      setPlayerBId(null);
    }
    onOpenChange(next);
  }

  const membersForA = members.filter((m) => m.id !== playerBId);
  const membersForB = members.filter((m) => m.id !== playerAId);

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
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
