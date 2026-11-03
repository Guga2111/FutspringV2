import { PlayerAvatar } from "@/components/PlayerAvatar"
import { PositionBadge } from "@/components/PositionBadge"
import type { ProfileDTO } from "@/types/user";
import type { PlayerPeladaStatsDTO } from "@/types/pelada";

export interface PlayerCompareData {
  profile: ProfileDTO;
  stats: PlayerPeladaStatsDTO;
}

interface ComparablePlayerStatsCardProps {
  playerA: PlayerCompareData;
  playerB: PlayerCompareData;
}

function Stars({ count }: { count: number }) {
  const clamped = Math.min(Math.max(Math.round(count), 0), 5);
  return (
    <span className="text-gold text-xs tracking-tight">
      {"★".repeat(clamped)}
      <span className="text-star-off">{"★".repeat(5 - clamped)}</span>
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
    <div className="grid grid-cols-[1fr_120px_1fr] gap-x-3 items-center py-1.5">
      {/* Player A value + bar */}
      <div className="flex items-center justify-end gap-2">
        <span className={`text-sm font-semibold tabular-nums ${valueA >= valueB ? "text-brand-text" : "text-muted-foreground"}`}>
          {valueA}
        </span>
        <div className="h-1.5 w-20 bg-border rounded-full overflow-hidden flex justify-end">
          <div
            className="h-full bg-brand rounded-full transition-all duration-500"
            style={{ width: `${pctA}%` }}
          />
        </div>
      </div>

      {/* Center label */}
      <span className="text-caption font-semibold text-muted-foreground whitespace-nowrap text-center">
        {label}
      </span>

      {/* Player B bar + value */}
      <div className="flex items-center gap-2">
        <div className="h-1.5 w-20 bg-border rounded-full overflow-hidden">
          <div
            className="h-full bg-highlight rounded-full transition-all duration-500"
            style={{ width: `${pctB}%` }}
          />
        </div>
        <span className={`text-sm font-semibold tabular-nums ${valueB >= valueA ? "text-highlight-text" : "text-muted-foreground"}`}>
          {valueB}
        </span>
      </div>
    </div>
  );
}

function PlayerColumn({
  data,
  stats,
  side,
}: {
  data: PlayerCompareData;
  stats: PlayerPeladaStatsDTO;
  side: "A" | "B";
}) {
  const ringColor = side === "A" ? "ring-brand/40" : "ring-highlight/40";

  return (
    <div className="flex flex-col items-center gap-2 min-w-0">
      <PlayerAvatar
        userId={data.profile.id}
        username={data.profile.username}
        image={data.profile.image}
        className={`size-16 text-lg ring-2 ${ringColor}`}
      />

      <div className="flex flex-col items-center gap-1">
        <span className="font-semibold text-sm text-foreground truncate max-w-[120px] text-center">
          {data.profile.username}
        </span>
        <PositionBadge position={data.profile.position} className="px-1.5 py-0 text-[10px]" />
        <Stars count={stats.stars} />
        <span className="text-[10px] text-muted-foreground tabular-nums">
          {stats.matchesPlayed} jogos
        </span>
      </div>
    </div>
  );
}

export function ComparablePlayerStatsCard({
  playerA,
  playerB,
}: ComparablePlayerStatsCardProps) {
  const stats: StatRow[] = [
    { label: "Gols", valueA: playerA.stats.goals, valueB: playerB.stats.goals },
    { label: "Assistências", valueA: playerA.stats.assists, valueB: playerB.stats.assists },
    {
      label: "Contribuições",
      valueA: playerA.stats.goals + playerA.stats.assists,
      valueB: playerB.stats.goals + playerB.stats.assists,
    },
    { label: "Campeão", valueA: playerA.stats.wins, valueB: playerB.stats.wins },
    { label: "Vitórias", valueA: playerA.stats.matchWins, valueB: playerB.stats.matchWins },
    { label: "Artilheiro", valueA: playerA.stats.artilheiroWins, valueB: playerB.stats.artilheiroWins },
    { label: "Garçom", valueA: playerA.stats.garcomWins, valueB: playerB.stats.garcomWins },
    { label: "Puskás", valueA: playerA.stats.puskasWins, valueB: playerB.stats.puskasWins },
  ];

  return (
    <div className="rounded-xl border bg-card-elevated p-4 space-y-4">
      {/* Player headers */}
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4">
        <PlayerColumn data={playerA} stats={playerA.stats} side="A" />

        <div className="flex flex-col items-center">
          <span className="font-display text-heading font-extrabold text-faint-foreground">VS</span>
        </div>

        <PlayerColumn data={playerB} stats={playerB.stats} side="B" />
      </div>

      {/* Divider */}
      <div className="border-t" />

      {/* Stat rows */}
      <div className="space-y-0.5">
        {stats.map((row) => (
          <StatBar key={row.label} {...row} />
        ))}
      </div>
    </div>
  );
}
