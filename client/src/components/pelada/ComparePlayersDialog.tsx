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
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>Comparar Jogadores</DialogTitle>
        </DialogHeader>

        {/* Player selectors */}
        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-1.5">
            <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Jogador A
            </span>
            <Select
              value={playerAId?.toString() ?? ""}
              onValueChange={(v) => setPlayerAId(Number(v))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Selecionar..." />
              </SelectTrigger>
              <SelectContent>
                {membersForA.map((m) => (
                  <SelectItem
                    key={m.id}
                    value={m.id.toString()}
                  >
                    {m.username}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-1.5">
            <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Jogador B
            </span>
            <Select
              value={playerBId?.toString() ?? ""}
              onValueChange={(v) => setPlayerBId(Number(v))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Selecionar..." />
              </SelectTrigger>
              <SelectContent>
                {membersForB.map((m) => (
                  <SelectItem
                    key={m.id}
                    value={m.id.toString()}
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
              <div className="rounded-xl border bg-card-elevated p-4 space-y-4">
                <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4">
                  <div className="flex flex-col items-center gap-2">
                    <Skeleton className="h-16 w-16 rounded-full" />
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-3 w-16" />
                  </div>
                  <span className="font-display text-heading font-extrabold text-faint-foreground">VS</span>
                  <div className="flex flex-col items-center gap-2">
                    <Skeleton className="h-16 w-16 rounded-full" />
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-3 w-16" />
                  </div>
                </div>
                <div className="border-t" />
                <div className="space-y-2">
                  {Array.from({ length: 7 }).map((_, i) => (
                    <Skeleton key={i} className="h-6 w-full" />
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
          <p className="text-center text-sm text-muted-foreground py-4">
            Selecione dois jogadores para comparar.
          </p>
        )}
      </DialogContent>
    </Dialog>
  );
}
