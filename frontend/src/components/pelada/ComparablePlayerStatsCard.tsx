import { Avatar, AvatarImage, AvatarFallback } from "../ui/avatar";
import { Badge } from "../ui/badge";
import { getPeladaGradient } from "@/lib/utils";
import type { ProfileDTO } from "@/types/user";
import type { StatsDTO } from "@/types/stats";

export interface PlayerCompareData {
  profile: ProfileDTO;
  stats: StatsDTO;
}

interface ComparablePlayerStatsCardProps {
  playerA: PlayerCompareData;
  playerB: PlayerCompareData;
  getFileUrl: (path: string | null | undefined) => string | undefined;
}

function Stars({ count }: { count: number }) {
  const clamped = Math.min(Math.max(Math.round(count), 0), 5);
  return (
    <span className="text-yellow-400 text-xs tracking-tight">
      {"★".repeat(clamped)}
      <span className="text-zinc-600">{"★".repeat(5 - clamped)}</span>
    </span>
  );
}

interface StatRow {
  label: string;
  valueA: number;
  valueB: number;
}

function StatBar({ valueA, valueB, label }: StatRow) {
  const total = valueA + valueB;
  const pctA = total === 0 ? 50 : (valueA / total) * 100;
  const pctB = total === 0 ? 50 : (valueB / total) * 100;

  return (
    <div className="grid grid-cols-[1fr_auto_1fr] gap-x-3 items-center py-1.5">
      {/* Player A value + bar */}
      <div className="flex items-center justify-end gap-2">
        <span className={`text-sm font-semibold tabular-nums ${valueA >= valueB ? "text-green-400" : "text-zinc-400"}`}>
          {valueA}
        </span>
        <div className="h-1.5 w-20 bg-zinc-800 rounded-full overflow-hidden flex justify-end">
          <div
            className="h-full bg-green-500 rounded-full transition-all duration-500"
            style={{ width: `${pctA}%` }}
          />
        </div>
      </div>

      {/* Center label */}
      <span className="text-[10px] font-medium text-zinc-500 uppercase tracking-widest whitespace-nowrap text-center">
        {label}
      </span>

      {/* Player B bar + value */}
      <div className="flex items-center gap-2">
        <div className="h-1.5 w-20 bg-zinc-800 rounded-full overflow-hidden">
          <div
            className="h-full bg-amber-500 rounded-full transition-all duration-500"
            style={{ width: `${pctB}%` }}
          />
        </div>
        <span className={`text-sm font-semibold tabular-nums ${valueB >= valueA ? "text-amber-400" : "text-zinc-400"}`}>
          {valueB}
        </span>
      </div>
    </div>
  );
}

function PlayerColumn({
  data,
  stats,
  getFileUrl,
  side,
}: {
  data: PlayerCompareData;
  stats: StatsDTO;
  getFileUrl: (path: string | null | undefined) => string | undefined;
  side: "A" | "B";
}) {
  const initials = data.profile.username.slice(0, 2).toUpperCase();
  const ringColor = side === "A" ? "ring-green-500/40" : "ring-amber-500/40";

  return (
    <div className="flex flex-col items-center gap-2 min-w-0">
      <Avatar className={`h-16 w-16 ring-2 ${ringColor}`}>
        {data.profile.image && (
          <AvatarImage
            src={getFileUrl(data.profile.image)}
            alt={data.profile.username}
          />
        )}
        <AvatarFallback
          className={`${getPeladaGradient(data.profile.username)} text-white text-lg font-bold`}
        >
          {initials}
        </AvatarFallback>
      </Avatar>

      <div className="flex flex-col items-center gap-1">
        <span className="font-semibold text-sm text-white truncate max-w-[120px] text-center">
          {data.profile.username}
        </span>
        {data.profile.position && (
          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
            {data.profile.position}
          </Badge>
        )}
        <Stars count={data.profile.stars} />
        <span className="text-[10px] text-zinc-500 tabular-nums">
          {stats.matchesPlayed} jogos
        </span>
      </div>
    </div>
  );
}

export function ComparablePlayerStatsCard({
  playerA,
  playerB,
  getFileUrl,
}: ComparablePlayerStatsCardProps) {
  const stats: StatRow[] = [
    { label: "Gols", valueA: playerA.stats.goals, valueB: playerB.stats.goals },
    { label: "Assistências", valueA: playerA.stats.assists, valueB: playerB.stats.assists },
    {
      label: "Contribuições",
      valueA: playerA.stats.goals + playerA.stats.assists,
      valueB: playerB.stats.goals + playerB.stats.assists,
    },
    { label: "Vitórias", valueA: playerA.stats.wins, valueB: playerB.stats.wins },
    { label: "Campeão", valueA: playerA.stats.matchWins, valueB: playerB.stats.matchWins },
    { label: "Artilheiro", valueA: playerA.stats.artilheiroWins, valueB: playerB.stats.artilheiroWins },
    { label: "Garçom", valueA: playerA.stats.garcomWins, valueB: playerB.stats.garcomWins },
  ];

  return (
    <div className="rounded-xl bg-zinc-900 border border-zinc-800 p-4 space-y-4">
      {/* Player headers */}
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4">
        <PlayerColumn data={playerA} stats={playerA.stats} getFileUrl={getFileUrl} side="A" />

        <div className="flex flex-col items-center">
          <span className="text-xl font-black text-zinc-600 tracking-tight">VS</span>
        </div>

        <PlayerColumn data={playerB} stats={playerB.stats} getFileUrl={getFileUrl} side="B" />
      </div>

      {/* Divider */}
      <div className="border-t border-zinc-800" />

      {/* Stat rows */}
      <div className="space-y-0.5">
        {stats.map((row) => (
          <StatBar key={row.label} {...row} />
        ))}
      </div>
    </div>
  );
}
