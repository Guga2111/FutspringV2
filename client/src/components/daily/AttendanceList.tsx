import { useMemo, type ReactNode } from "react"
import { ChevronRight, UserMinus, UserPlus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { StarRow } from "@/components/StarRow"
import { cn } from "@/lib/utils"
import { splitAttendance } from "@/utils/attendance"
import { positionShortLabel } from "@/utils/memberFilters"
import type { DailyDetail, PlayerDTO } from "@/types/daily"

interface AttendanceListProps {
  daily: DailyDetail
  open: boolean
  onOpenChange: (open: boolean) => void
  // admin actions only while attendance is open (SCHEDULED/CONFIRMED)
  canManage: boolean
  togglingId: number | null
  confirmAllPending: boolean
  onConfirm: (userId: number) => void
  onUnconfirm: (userId: number) => void
  onConfirmAll: () => void
}

export function AttendanceList({
  daily,
  open,
  onOpenChange,
  canManage,
  togglingId,
  confirmAllPending,
  onConfirm,
  onUnconfirm,
  onConfirmAll,
}: AttendanceListProps) {
  const { confirmed, pending } = useMemo(
    () => splitAttendance(daily.peladaMembers, daily.confirmedPlayers),
    [daily.peladaMembers, daily.confirmedPlayers],
  )

  return (
    <Collapsible open={open} onOpenChange={onOpenChange} className="flex flex-col gap-3">
      <CollapsibleTrigger className="flex w-full items-center gap-2.5 text-left">
        <ChevronRight className={cn("size-4 shrink-0 transition-transform duration-150", open && "rotate-90")} />
        <span className="text-base font-semibold">Lista de presença</span>
        <span className="text-[13px] text-subtle-foreground">
          {confirmed.length} confirmados · {pending.length} pendentes
        </span>
        <span className="ml-auto text-[13px] text-muted-foreground">{open ? "Recolher" : "Expandir"}</span>
      </CollapsibleTrigger>
      <CollapsibleContent className="grid grid-cols-1 items-start gap-4 min-[1100px]:grid-cols-2">
        <div className="overflow-hidden rounded-xl border">
          <div className="flex items-center gap-2 border-b bg-card px-3.5 py-3">
            <span className="size-2 rounded-full bg-success-strong" />
            <span className="text-sm font-semibold">Confirmados</span>
            <span className="text-[13px] text-subtle-foreground">{confirmed.length}</span>
          </div>
          {confirmed.length === 0 ? (
            <p className="px-3.5 py-7 text-center text-[13px] text-subtle-foreground">Ninguém confirmou ainda.</p>
          ) : (
            <ul>
              {confirmed.map((player) => (
                <AttendanceRow key={player.id} player={player}>
                  {canManage && (
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Remover presença de ${player.username}`}
                      disabled={togglingId === player.id}
                      onClick={() => onUnconfirm(player.id)}
                      className="size-7 text-subtle-foreground hover:bg-destructive-muted hover:text-destructive-soft"
                    >
                      <UserMinus className="size-[15px]" />
                    </Button>
                  )}
                </AttendanceRow>
              ))}
            </ul>
          )}
        </div>

        <div className="overflow-hidden rounded-xl border">
          <div className="flex min-h-[47px] items-center gap-2 border-b bg-card py-2 pl-3.5 pr-2">
            <span className="size-2 rounded-full bg-faint-foreground/70" />
            <span className="text-sm font-semibold">Não confirmados</span>
            <span className="text-[13px] text-subtle-foreground">{pending.length}</span>
            {canManage && pending.length > 0 && (
              <Button
                variant="outline"
                disabled={confirmAllPending}
                onClick={onConfirmAll}
                className="ml-auto h-[30px] bg-transparent px-3 text-[13px] hover:bg-accent"
              >
                Confirmar todos
              </Button>
            )}
          </div>
          {pending.length === 0 ? (
            <p className="px-3.5 py-7 text-center text-[13px] text-subtle-foreground">Todos os membros confirmaram.</p>
          ) : (
            <ul>
              {pending.map((player) => (
                <AttendanceRow key={player.id} player={player} muted>
                  {canManage && (
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Confirmar ${player.username}`}
                      disabled={togglingId === player.id}
                      onClick={() => onConfirm(player.id)}
                      className="size-7 text-subtle-foreground hover:bg-success-muted hover:text-success"
                    >
                      <UserPlus className="size-[15px]" />
                    </Button>
                  )}
                </AttendanceRow>
              ))}
            </ul>
          )}
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}

function AttendanceRow({ player, muted = false, children }: { player: PlayerDTO; muted?: boolean; children?: ReactNode }) {
  const position = positionShortLabel(player.position)
  return (
    <li className="flex items-center gap-2.5 border-b border-border/60 px-3.5 py-[9px] last:border-b-0">
      <PlayerAvatar
        username={player.username}
        image={player.image}
        className={cn("size-[30px]", muted && "[&>span]:bg-accent [&>span]:text-muted-foreground")}
      />
      <span className={cn("min-w-0 truncate text-sm", muted ? "text-secondary-foreground" : "font-medium")}>{player.username}</span>
      {position && (
        <Badge
          variant="status"
          className={cn(
            "px-[7px] py-0.5 text-[10px] font-semibold uppercase tracking-[0.03em]",
            muted ? "bg-accent text-muted-foreground" : "bg-secondary text-secondary-foreground",
          )}
        >
          {position}
        </Badge>
      )}
      <StarRow stars={player.stars} muted={muted} className="ml-auto text-[11px]" />
      {children}
    </li>
  )
}
