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

const POSITION_COLORS: Record<string, string> = {
  Goleiro: "bg-yellow-500/20 text-yellow-400 border-yellow-500/30",
  Zagueiro: "bg-blue-500/20 text-blue-400 border-blue-500/30",
  "Lateral-Direito": "bg-cyan-500/20 text-cyan-400 border-cyan-500/30",
  "Lateral-Esquerdo": "bg-cyan-500/20 text-cyan-400 border-cyan-500/30",
  Volante: "bg-purple-500/20 text-purple-400 border-purple-500/30",
  Meia: "bg-indigo-500/20 text-indigo-400 border-indigo-500/30",
  "Meia-Atacante": "bg-orange-500/20 text-orange-400 border-orange-500/30",
  Atacante: "bg-red-500/20 text-red-400 border-red-500/30",
  Ponta: "bg-pink-500/20 text-pink-400 border-pink-500/30",
};

function getPositionColor(position: string | null): string {
  if (!position) return "bg-zinc-500/20 text-zinc-400 border-zinc-500/30";
  return (
    POSITION_COLORS[position] ?? "bg-zinc-500/20 text-zinc-400 border-zinc-500/30"
  );
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
        <span className={`font-mono text-sm font-bold ${valueA >= valueB ? "text-green-400" : "text-zinc-400"}`}>
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
      <span className="text-[10px] font-medium text-zinc-500 uppercase tracking-widest text-center whitespace-nowrap">
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
        <span className={`font-mono text-sm font-bold ${valueB >= valueA ? "text-amber-400" : "text-zinc-400"}`}>
          {valueB}
        </span>
      </div>
    </div>
  );
}

function PlayerColumn({
  data,
  getFileUrl,
  side,
}: {
  data: PlayerCompareData;
  getFileUrl: (path: string | null | undefined) => string | undefined;
  side: "A" | "B";
}) {
  const initials = data.profile.username.slice(0, 2).toUpperCase();
  const positionColor = getPositionColor(data.profile.position);
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
          <Badge
            variant="outline"
            className={`text-[10px] px-1.5 py-0 border ${positionColor}`}
          >
            {data.profile.position}
          </Badge>
        )}
        <Stars count={data.profile.stars} />
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
    { label: "Artilheiro 🏅", valueA: playerA.stats.artilheiroWins, valueB: playerB.stats.artilheiroWins },
    { label: "Garçom 🍽️", valueA: playerA.stats.garcomWins, valueB: playerB.stats.garcomWins },
  ];

  return (
    <div className="rounded-xl bg-zinc-900 border border-zinc-800 p-4 space-y-4">
      {/* Player headers */}
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4">
        <PlayerColumn data={playerA} getFileUrl={getFileUrl} side="A" />

        <div className="flex flex-col items-center gap-0.5">
          <span className="font-mono text-xl font-black text-zinc-500 tracking-tighter">VS</span>
        </div>

        <PlayerColumn data={playerB} getFileUrl={getFileUrl} side="B" />
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
