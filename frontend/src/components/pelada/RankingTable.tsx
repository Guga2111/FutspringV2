import { Link } from "react-router-dom";
import { Trophy, ChevronUp, ChevronDown, ChevronsUpDown } from "lucide-react";
import { Skeleton } from "../ui/skeleton";
import { Avatar, AvatarImage, AvatarFallback } from "../ui/avatar";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../ui/table";
import type { RankingDTO } from "@/types/daily";

interface RankingTabProps {
  ranking: RankingDTO[];
  isLoading: boolean;
  sortConfig: {
    col: "goals" | "assists" | "matchesPlayed" | "wins";
    dir: "asc" | "desc";
  };
  onSort: (col: "goals" | "assists" | "matchesPlayed" | "wins") => void;
  getFileUrl: (path: string | null | undefined) => string | undefined;
}

export function RankingTable({
  ranking,
  isLoading,
  sortConfig,
  onSort,
  getFileUrl,
}: RankingTabProps) {
  return (
    <div className="mt-2">
      <p className="text-xs text-muted-foreground mb-3">
        Top jogadores · {ranking.length} classificados
      </p>

      {isLoading ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex items-center gap-3">
              <Skeleton className="h-8 w-8 rounded-full flex-shrink-0" />
              <Skeleton className="h-4 flex-1" />
              <Skeleton className="h-4 w-8" />
              <Skeleton className="h-4 w-8" />
              <Skeleton className="h-4 w-8" />
              <Skeleton className="h-4 w-8" />
            </div>
          ))}
        </div>
      ) : ranking.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Sem dados de ranking ainda.
        </p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="h-8 px-2 text-xs text-center w-8">#</TableHead>
              <TableHead className="h-8 px-2 text-xs">Jogador</TableHead>
              {(
                [
                  { col: "matchesPlayed", label: "J" },
                  { col: "wins", label: "V" },
                  { col: "goals", label: "Gols" },
                  { col: "assists", label: "Assist." },
                ] as const
              ).map(({ col, label }) => {
                const active = sortConfig.col === col;
                return (
                  <TableHead
                    key={col}
                    className="h-8 px-2 text-xs text-center cursor-pointer select-none hover:text-foreground"
                    onClick={() => onSort(col)}
                  >
                    {label}
                    {active
                      ? sortConfig.dir === "desc"
                        ? <ChevronDown className="h-3 w-3 inline ml-1" />
                        : <ChevronUp className="h-3 w-3 inline ml-1" />
                      : <ChevronsUpDown className="h-3 w-3 inline ml-1 opacity-40" />
                    }
                  </TableHead>
                );
              })}
            </TableRow>
          </TableHeader>
          <TableBody>
            {ranking.map((row, idx) => {
              const initials = row.username.slice(0, 2).toUpperCase();
              return (
                <TableRow key={row.userId} className="last:border-0">
                  <TableCell className="py-2 px-2 text-center">
                    {idx === 0 ? (
                      <Trophy className="h-4 w-4 text-yellow-500 mx-auto" />
                    ) : idx === 1 ? (
                      <Trophy className="h-4 w-4 text-slate-400 mx-auto" />
                    ) : idx === 2 ? (
                      <Trophy className="h-4 w-4 text-amber-700 mx-auto" />
                    ) : (
                      <span className="text-muted-foreground">{idx + 1}</span>
                    )}
                  </TableCell>
                  <TableCell className="py-2 px-2">
                    <div className="flex items-center gap-2">
                      <Avatar className="h-7 w-7 flex-shrink-0">
                        {row.userImage && getFileUrl(row.userImage) && (
                          <AvatarImage
                            src={getFileUrl(row.userImage)}
                            alt={row.username}
                          />
                        )}
                        <AvatarFallback className="text-xs font-semibold">
                          {initials}
                        </AvatarFallback>
                      </Avatar>
                      <Link
                        to={`/profile/${row.userId}`}
                        className="font-medium hover:underline"
                      >
                        {row.username}
                      </Link>
                    </div>
                  </TableCell>
                  <TableCell className="py-2 px-2 text-center">{row.matchesPlayed}</TableCell>
                  <TableCell className="py-2 px-2 text-center">{row.wins}</TableCell>
                  <TableCell className="py-2 px-2 text-center">{row.goals}</TableCell>
                  <TableCell className="py-2 px-2 text-center">{row.assists}</TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
