import { Link } from "react-router-dom"
import {
  ArrowLeft,
  CircleCheck,
  CircleX,
  ClipboardList,
  Clock,
  FileText,
  Flag,
  MoreHorizontal,
  Pencil,
  Play,
  Trash2,
  Upload,
  Users,
  Volleyball,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { DailyStatusBadge } from "@/components/daily/DailyStatusBadge"
import { formatLongDate } from "@/utils/sessions"
import type { DailyDetail } from "@/types/daily"

export interface DailyHeaderActions {
  onImportFromMessage: () => void
  onConfirmDaily: () => void
  onStartSession: () => void
  onCancelDaily: () => void
  onEnterResults: () => void
  onFinalizeDaily: () => void
  onChangeChampionPhoto: () => void
  onDeleteDaily: () => void
}

interface DailyHeaderProps extends DailyHeaderActions {
  daily: DailyDetail
}

const actionButton = "h-[38px] gap-2 px-4 [&_svg]:size-[15px]"
const outlineAction = `${actionButton} bg-transparent hover:bg-accent`
const menuItem = "gap-2.5 p-2 text-sm [&>svg]:size-4 [&>svg]:text-muted-foreground"

function playersMeta(daily: DailyDetail): string {
  if (daily.teams.length === 0) return `${daily.numberOfTeams} times de ${daily.playersPerTeam}`
  const players = daily.teams.reduce((sum, team) => sum + team.players.length, 0)
  return `${players} jogadores · ${daily.teams.length} times`
}

// Back link, date, status, metadata and the admin actions (they used to live in a fixed bottom bar)
export function DailyHeader({ daily, ...actions }: DailyHeaderProps) {
  const { status } = daily
  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div className="flex min-w-0 flex-col gap-2">
        <Link
          to={`/pelada/${daily.peladaId}`}
          className="flex items-center gap-1.5 text-[13px] text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft className="size-3.5" />
          {daily.peladaName}
        </Link>
        <div className="flex flex-wrap items-center gap-2.5">
          <h1 className="text-[22px] font-bold tracking-[-0.01em] md:text-[28px]">{formatLongDate(daily.dailyDate)}</h1>
          <DailyStatusBadge status={status} size="lg" />
          {status === "IN_COURSE" && (
            <span className="flex items-center gap-1.5 text-[13px] font-semibold text-success">
              <span className="size-2 rounded-full bg-success-strong shadow-[0_0_0_4px_hsl(var(--success-strong)/0.18)]" />
              Ao vivo
            </span>
          )}
        </div>
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-muted-foreground [&_svg]:size-3.5">
          <span className="flex items-center gap-1.5">
            <Clock />
            {daily.dailyTime}
          </span>
          <span className="flex items-center gap-1.5">
            <Users />
            {playersMeta(daily)}
          </span>
          {status === "FINISHED" && (
            <span className="flex items-center gap-1.5">
              <Volleyball />
              {daily.matches.length} {daily.matches.length === 1 ? "partida" : "partidas"}
            </span>
          )}
        </div>
      </div>

      {daily.isAdmin && (
        <div className="flex flex-wrap gap-2">
          {status === "SCHEDULED" && (
            <>
              <Button variant="outline" className={outlineAction} onClick={actions.onImportFromMessage}>
                <FileText />
                Importar mensagem
              </Button>
              <Button variant="gradient" className={`${actionButton} font-semibold`} onClick={actions.onConfirmDaily}>
                <CircleCheck strokeWidth={2.2} />
                Confirmar diária
              </Button>
            </>
          )}
          {status === "CONFIRMED" && (
            <Button variant="gradient" className={`${actionButton} font-semibold`} onClick={actions.onStartSession}>
              <Play />
              Iniciar sessão
            </Button>
          )}
          {status === "IN_COURSE" && (
            <Button variant="gradient" className={`${actionButton} font-semibold`} onClick={actions.onEnterResults}>
              <ClipboardList />
              Lançar resultados
            </Button>
          )}
          {status === "FINISHED" && (
            <>
              <Button variant="outline" className={outlineAction} onClick={actions.onEnterResults}>
                <Pencil />
                Editar resultados
              </Button>
              <Button variant="outline" className={outlineAction} onClick={actions.onFinalizeDaily}>
                <Flag />
                Re-finalizar
              </Button>
            </>
          )}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="icon" aria-label="Mais ações" className="size-[38px] bg-transparent hover:bg-accent">
                <MoreHorizontal className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" sideOffset={6} className="w-[220px] rounded-[10px] p-1 shadow-menu">
              {(status === "SCHEDULED" || status === "CONFIRMED") && (
                <>
                  <DropdownMenuItem className={menuItem} onSelect={actions.onCancelDaily}>
                    <CircleX />
                    Cancelar diária
                  </DropdownMenuItem>
                  <DropdownMenuSeparator className="-mx-1 bg-border" />
                </>
              )}
              {status === "FINISHED" && (
                <>
                  <DropdownMenuItem className={menuItem} onSelect={actions.onChangeChampionPhoto}>
                    <Upload />
                    Trocar foto do campeão
                  </DropdownMenuItem>
                  <DropdownMenuSeparator className="-mx-1 bg-border" />
                </>
              )}
              <DropdownMenuItem
                className={`${menuItem} text-destructive focus:bg-destructive-muted focus:text-destructive [&>svg]:text-destructive`}
                onSelect={actions.onDeleteDaily}
              >
                <Trash2 />
                Excluir sessão
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      )}
    </header>
  )
}
