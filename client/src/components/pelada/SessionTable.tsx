import { Plus, Users } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { DailyStatusBadge } from "@/components/daily/DailyStatusBadge"
import type { DailyListItem } from "@/types/daily"

interface SessionsTabProps {
  dailies: DailyListItem[]
  isLoading: boolean
  isAdmin: boolean
  onOpenCreate: () => void
  onNavigate: (id: number) => void
}

function formatSessionDate(date: string): string {
  return new Date(date + "T12:00:00").toLocaleDateString("pt-BR", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  })
}

export function SessionsTable({ dailies, isLoading, isAdmin, onOpenCreate, onNavigate }: SessionsTabProps) {
  return (
    <>
      <div className="mb-3 mt-2 flex items-center justify-between">
        <span className="text-sm text-muted-foreground">{dailies.length} sessões</span>
        {isAdmin && (
          <Button variant="gradient" size="icon" aria-label="Criar sessão" onClick={onOpenCreate}>
            <Plus className="size-5" />
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Data</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Jogadores</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {dailies.map((daily) => (
              <TableRow
                key={daily.id}
                role="link"
                tabIndex={0}
                className="cursor-pointer"
                onClick={() => onNavigate(daily.id)}
                onKeyDown={(e) => e.key === "Enter" && onNavigate(daily.id)}
              >
                <TableCell className="font-medium capitalize">{formatSessionDate(daily.dailyDate)}</TableCell>
                <TableCell>
                  <DailyStatusBadge status={daily.status} />
                </TableCell>
                <TableCell>
                  <span className="flex items-center gap-1.5 text-muted-foreground">
                    <Users className="size-3.5" /> {daily.confirmedPlayerCount}
                  </span>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </>
  )
}
