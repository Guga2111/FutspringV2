import { Link } from "react-router-dom"
import { History } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { cn } from "@/lib/utils"
import type { RankingDTO } from "@/types/daily"

export type RankingCol = "goals" | "assists" | "matchesPlayed" | "wins"

interface RankingTableProps {
  ranking: RankingDTO[]
  isLoading: boolean
  sortConfig: { col: RankingCol; dir: "asc" | "desc" }
  onSort: (col: RankingCol) => void
  onOpenHistory: (userId: number) => void
}

const COLUMNS: { col: RankingCol; label: string; width: string }[] = [
  { col: "matchesPlayed", label: "J", width: "w-[30px] md:w-16" },
  { col: "wins", label: "V", width: "w-[26px] md:w-16" },
  { col: "goals", label: "Gols", width: "w-10 md:w-16" },
  { col: "assists", label: "Assist.", width: "w-11 md:w-16" },
]

const MEDALS = ["text-gold", "text-silver", "text-bronze"] as const

// Every column fits at 375 px (no horizontal scroll): narrow fixed columns on mobile
const cell = "px-1.5 py-2.5 md:px-3"

export function RankingTable({ ranking, isLoading, sortConfig, onSort, onOpenHistory }: RankingTableProps) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-2 rounded-xl border p-3">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="flex items-center gap-3">
            <Skeleton className="size-7 shrink-0 rounded-full" />
            <Skeleton className="h-4 flex-1" />
            <Skeleton className="h-4 w-24" />
          </div>
        ))}
      </div>
    )
  }

  if (ranking.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-input p-10 text-center text-sm text-subtle-foreground">
        Sem dados de ranking ainda.
      </div>
    )
  }

  return (
    <div className="overflow-hidden rounded-xl border">
      <Table className="table-fixed">
        <TableHeader className="bg-card [&_tr]:border-b">
          <TableRow className="hover:bg-transparent">
            <TableHead className={cn(cell, "h-auto w-[26px] text-center text-xs font-normal text-subtle-foreground md:w-11")}>#</TableHead>
            <TableHead className={cn(cell, "h-auto text-xs font-normal text-subtle-foreground")}>Jogador</TableHead>
            <TableHead className={cn(cell, "h-auto w-7 md:w-10")}>
              <span className="sr-only">Histórico</span>
            </TableHead>
            {COLUMNS.map(({ col, label, width }) => {
              const active = sortConfig.col === col
              return (
                <TableHead
                  key={col}
                  aria-sort={active ? (sortConfig.dir === "desc" ? "descending" : "ascending") : "none"}
                  className={cn(cell, "h-auto text-center text-xs font-normal", width)}
                >
                  <button
                    type="button"
                    onClick={() => onSort(col)}
                    className={cn(
                      "whitespace-nowrap hover:text-foreground",
                      active ? "text-foreground" : "text-subtle-foreground",
                    )}
                  >
                    {label}
                    {active && (sortConfig.dir === "desc" ? " ↓" : " ↑")}
                  </button>
                </TableHead>
              )
            })}
          </TableRow>
        </TableHeader>
        <TableBody>
          {ranking.map((row, idx) => (
            <TableRow key={row.userId} className="border-border/60 text-sm hover:bg-row-hover">
              <TableCell
                className={cn(cell, "text-center tabular-nums", idx < 3 ? cn("font-bold", MEDALS[idx]) : "text-subtle-foreground")}
              >
                {idx < 3 ? `${idx + 1}º` : idx + 1}
              </TableCell>
              <TableCell className={cell}>
                <div className="flex min-w-0 items-center gap-2.5">
                  <PlayerAvatar username={row.username} image={row.userImage} />
                  <Link to={`/profile/${row.userId}`} className="truncate font-medium hover:underline">
                    {row.username}
                  </Link>
                </div>
              </TableCell>
              <TableCell className={cn(cell, "px-0 md:px-1.5")}>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`Ver histórico de ${row.username}`}
                  onClick={() => onOpenHistory(row.userId)}
                  className="size-7 text-subtle-foreground hover:bg-secondary hover:text-foreground"
                >
                  <History className="size-[15px]" />
                </Button>
              </TableCell>
              {COLUMNS.map(({ col }) => (
                <TableCell
                  key={col}
                  className={cn(cell, "text-center tabular-nums", sortConfig.col === col && "font-semibold")}
                >
                  {row[col]}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
