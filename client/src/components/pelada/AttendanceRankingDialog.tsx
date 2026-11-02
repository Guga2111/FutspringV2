import { useMemo, useState } from "react"
import { Link } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowDown02Icon, ArrowUp02Icon, Search01Icon } from "@hugeicons/core-free-icons"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
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
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { MembershipBadge } from "@/components/MembershipBadge"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { cn } from "@/lib/utils"
import { usePeladaAttendance } from "@/components/pelada/hooks/usePeladaAttendance"
import {
  attendancePeriodLabel,
  formatAttendanceRate,
  viewAttendance,
  type AttendanceOrder,
} from "@/utils/attendanceRanking"
import { MEMBERSHIP_FILTERS, countByMembership, type MembershipFilter } from "@/utils/memberFilters"
import type { PeladaAttendanceDTO } from "@/types/pelada"

interface AttendanceRankingDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  peladaId: number
}

const MEDALS = ["text-gold", "text-silver", "text-bronze"] as const

const FILTERS: { value: MembershipFilter; label: string }[] = [{ value: "ALL", label: "Todos" }, ...MEMBERSHIP_FILTERS]

const cell = "px-1.5 py-2.5 md:px-3"

export function AttendanceRankingDialog({ open, onOpenChange, peladaId }: AttendanceRankingDialogProps) {
  // Refetches on every open; the previous ranking stays on screen meanwhile
  const { data, loading, error, retry } = usePeladaAttendance(peladaId, open)

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
          <AttendanceRanking data={data} onNavigate={() => onOpenChange(false)} />
        )}
      </DialogContent>
    </Dialog>
  )
}

// Mounted only while the dialog is open, so the filter, order and search reset on every open
function AttendanceRanking({ data, onNavigate }: { data: PeladaAttendanceDTO; onNavigate: () => void }) {
  const [membership, setMembership] = useState<MembershipFilter>("ALL")
  const [order, setOrder] = useState<AttendanceOrder>("desc")
  const [query, setQuery] = useState("")

  const rows = useMemo(() => viewAttendance(data.rows, { membership, order, query }), [data, membership, order, query])
  const counts = useMemo(() => countByMembership(data.rows), [data])

  return (
    <>
      <div className="flex flex-col gap-3">
        <div className="relative">
          <HugeiconsIcon icon={Search01Icon} aria-hidden className="pointer-events-none absolute left-3 top-1/2 size-[15px] -translate-y-1/2 text-subtle-foreground" />
          <Input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Buscar jogador"
            aria-label="Buscar jogador"
            className="h-[38px] rounded-full bg-card pl-[34px] pr-3.5"
          />
        </div>
        <ToggleGroup
          type="single"
          variant="chip"
          size="chip"
          value={membership}
          onValueChange={(value) => value && setMembership(value as MembershipFilter)}
          aria-label="Filtrar por tipo"
          className="scrollbar-none justify-start gap-1.5 overflow-x-auto"
        >
          {FILTERS.map((f) => (
            <ToggleGroupItem key={f.value} value={f.value} disabled={counts[f.value] === 0} className="group shrink-0">
              {f.label}
              <span className="text-xs text-subtle-foreground group-data-[state=on]:text-primary-foreground/60">{counts[f.value]}</span>
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
      </div>

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
              <TableHead
                aria-sort={order === "desc" ? "descending" : "ascending"}
                className={cn(cell, "h-auto w-14 text-right text-xs font-normal text-subtle-foreground md:w-40")}
              >
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setOrder((o) => (o === "desc" ? "asc" : "desc"))}
                  aria-label={
                    order === "desc"
                      ? "Ordenar por presença: mais presentes primeiro"
                      : "Ordenar por presença: menos presentes primeiro"
                  }
                  className="-mr-2 h-auto gap-1 px-2 py-1 text-xs font-normal text-subtle-foreground"
                >
                  %
                  {order === "desc" ? <HugeiconsIcon icon={ArrowDown02Icon} aria-hidden className="size-3.5" /> : <HugeiconsIcon icon={ArrowUp02Icon} aria-hidden className="size-3.5" />}
                </Button>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={4} className="py-8 text-center text-sm text-subtle-foreground">
                  Nenhum jogador encontrado para "{query.trim()}"
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row) => {
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
                        <Link to={`/profile/${row.userId}`} onClick={onNavigate} className="truncate font-medium hover:underline">
                          {row.username}
                        </Link>
                        <MembershipBadge isMensalista={row.isMensalista} className="hidden shrink-0 sm:inline-flex" />
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
                        <span className="w-10 text-right font-semibold tabular-nums">{formatAttendanceRate(row.rate)}</span>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>
    </>
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
