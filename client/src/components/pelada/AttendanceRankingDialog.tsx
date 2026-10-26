import { useMemo } from "react"
import { Link } from "react-router-dom"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Progress } from "@/components/ui/progress"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { cn } from "@/lib/utils"
import { usePeladaAttendance } from "@/components/pelada/hooks/usePeladaAttendance"
import {
  attendancePeriodLabel,
  formatAttendanceRate,
  rankAttendance,
} from "@/utils/attendanceRanking"

interface AttendanceRankingDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  peladaId: number
}

const MEDALS = ["text-gold", "text-silver", "text-bronze"] as const

const cell = "px-1.5 py-2.5 md:px-3"

export function AttendanceRankingDialog({ open, onOpenChange, peladaId }: AttendanceRankingDialogProps) {
  // Refetches on every open; the previous ranking stays on screen meanwhile
  const { data, loading, error, retry } = usePeladaAttendance(peladaId, open)
  const rows = useMemo(() => (data ? rankAttendance(data.rows) : []), [data])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[90vh] max-w-xl flex-col">
        <DialogHeader>
          <DialogTitle>Ranking de presença</DialogTitle>
          <DialogDescription>
            {data
              ? `${attendancePeriodLabel(data.from, data.to)} · ${
                  data.totalSessions === 1 ? "1 sessão finalizada" : `${data.totalSessions} sessões finalizadas`
                }`
              : "Sessões finalizadas nos últimos 6 meses."}
          </DialogDescription>
        </DialogHeader>

        {loading && !data ? (
          <AttendanceSkeleton />
        ) : error ? (
          <Alert variant="destructive">
            <AlertDescription className="flex flex-wrap items-center justify-between gap-3">
              Não foi possível carregar o ranking de presença.
              <Button variant="outline" size="sm" onClick={retry}>
                Tentar novamente
              </Button>
            </AlertDescription>
          </Alert>
        ) : !data || data.totalSessions === 0 ? (
          <div className="rounded-xl border border-dashed border-input p-10 text-center text-sm text-subtle-foreground">
            Nenhuma sessão finalizada nos últimos 6 meses.
          </div>
        ) : (
          <div className="min-h-0 overflow-y-auto rounded-xl border">
            <Table className="table-fixed">
              <TableHeader className="sticky top-0 bg-card-elevated [&_tr]:border-b">
                <TableRow className="hover:bg-transparent">
                  <TableHead className={cn(cell, "h-auto w-9 text-center text-xs font-normal text-subtle-foreground md:w-11")}>
                    #
                  </TableHead>
                  <TableHead className={cn(cell, "h-auto text-xs font-normal text-subtle-foreground")}>Jogador</TableHead>
                  <TableHead className={cn(cell, "h-auto w-[72px] text-center text-xs font-normal text-subtle-foreground")}>
                    Presenças
                  </TableHead>
                  <TableHead className={cn(cell, "h-auto w-14 text-right text-xs font-normal text-subtle-foreground md:w-40")}>
                    %
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => {
                  const medal = row.sessionsAttended > 0 && row.position <= 3 ? MEDALS[row.position - 1] : null
                  return (
                    <TableRow key={row.userId} className="border-border/60 text-sm hover:bg-row-hover">
                      <TableCell
                        className={cn(cell, "text-center tabular-nums", medal ? cn("font-bold", medal) : "text-subtle-foreground")}
                      >
                        {medal ? `${row.position}º` : row.position}
                      </TableCell>
                      <TableCell className={cell}>
                        <div className="flex min-w-0 items-center gap-2.5">
                          <PlayerAvatar username={row.username} image={row.userImage} />
                          <Link
                            to={`/profile/${row.userId}`}
                            onClick={() => onOpenChange(false)}
                            className="truncate font-medium hover:underline"
                          >
                            {row.username}
                          </Link>
                        </div>
                      </TableCell>
                      <TableCell className={cn(cell, "text-center tabular-nums text-muted-foreground")}>
                        {row.sessionsAttended}/{data.totalSessions}
                      </TableCell>
                      <TableCell className={cell}>
                        <div className="flex items-center justify-end gap-2.5">
                          <Progress
                            value={row.rate * 100}
                            className="hidden h-1.5 flex-1 md:block"
                            indicatorClassName="bg-gradient-primary"
                            aria-label={`Presença de ${row.username}`}
                          />
                          <span className="w-10 text-right font-semibold tabular-nums">
                            {formatAttendanceRate(row.rate)}
                          </span>
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

function AttendanceSkeleton() {
  return (
    <div className="flex flex-col gap-2 rounded-xl border p-3">
      {[0, 1, 2, 3, 4].map((i) => (
        <div key={i} className="flex items-center gap-3">
          <Skeleton className="size-7 shrink-0 rounded-full" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-20" />
        </div>
      ))}
    </div>
  )
}
