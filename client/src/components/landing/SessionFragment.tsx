import { PlayerAvatar } from "@/components/PlayerAvatar"
import { DailyStatusBadge } from "@/components/daily/DailyStatusBadge"
import { TeamColorDot } from "@/components/daily/teams/TeamColorDot"
import { buttonVariants } from "@/components/ui/button-variants"
import { Progress } from "@/components/ui/progress"
import { StarCount } from "@/components/landing/StarCount"
import { cn } from "@/lib/utils"
import type { DailyStatus } from "@/types/daily"

// The same session card at four moments of a match day (hero + "Um dia de jogo").
// It is a picture of the product, not a control: role="img" with a description, and the
// "button" inside is a styled span.
export type SessionStage = "list" | "draw" | "live" | "final"

const STAGE: Record<SessionStage, { status: DailyStatus; label: string }> = {
  list: { status: "SCHEDULED", label: "Sessão agendada: 14 de 20 confirmados" },
  draw: { status: "CONFIRMED", label: "Sessão confirmada: times sorteados, Azul e Laranja com 17 estrelas cada" },
  live: { status: "IN_COURSE", label: "Sessão ao vivo: Azul 3, Laranja 2" },
  final: { status: "FINISHED", label: "Sessão finalizada: Time Azul campeão, Leal artilheiro com 4 gols" },
}

const BLUE = "#1D6FE0"
const ORANGE = "#F07A2B"
const CONFIRMED = [1, 2, 3, 4, 5]

export function SessionFragment({ stage, className }: { stage: SessionStage; className?: string }) {
  const { status, label } = STAGE[stage]
  return (
    <div
      data-ui
      role="img"
      aria-label={label}
      className={cn("rounded-hero border bg-card p-5 text-left text-card-foreground", className)}
    >
      <div className="flex items-center justify-between gap-3">
        <p className="truncate text-title">Pelada de Quinta</p>
        <DailyStatusBadge status={status} size="sm" />
      </div>
      <p className="mt-1 text-body-sm text-muted-foreground">Qui, 06/11 · 20:00 · Society do Zé</p>

      {stage === "list" && (
        <div className="mt-5">
          <div className="flex items-baseline justify-between">
            <span className="font-display text-score font-bold tabular-nums">14/20</span>
            <span className="text-body-sm text-muted-foreground">6 vagas</span>
          </div>
          <Progress value={70} className="mt-2 h-2" indicatorClassName="bg-brand" />
          <div className="mt-4 flex -space-x-2">
            {CONFIRMED.map((id, i) => (
              <PlayerAvatar key={id} userId={id} username={["Leal", "Guga", "Rafa", "Bruno", "Tiago"][i]} image={null} className="size-8 border-2 border-card text-[11px]" />
            ))}
            <span className="flex size-8 items-center justify-center rounded-full border-2 border-card bg-avatar-fallback text-[11px] font-semibold">+9</span>
          </div>
          <span className={cn(buttonVariants({ variant: "brand" }), "mt-5 w-full")}>Confirmar presença</span>
        </div>
      )}

      {stage === "draw" && (
        <ul className="mt-5 space-y-2">
          {[
            { name: "Time Azul", color: BLUE },
            { name: "Time Laranja", color: ORANGE },
          ].map((team) => (
            <li key={team.name} className="flex items-center justify-between rounded-tile bg-card-elevated px-3 py-2.5">
              <span className="flex items-center gap-2 text-body font-medium">
                <TeamColorDot color={team.color} />
                {team.name}
              </span>
              <StarCount stars={17} className="text-body font-semibold" />
            </li>
          ))}
        </ul>
      )}

      {stage === "live" && (
        <div className="mt-5">
          <div className="flex items-center justify-between rounded-tile bg-card-elevated px-4 py-3">
            <span className="flex items-center gap-2 text-body font-medium"><TeamColorDot color={BLUE} />Azul</span>
            <span className="font-display text-score font-bold tabular-nums">3 × 2</span>
            <span className="flex items-center gap-2 text-body font-medium">Laranja<TeamColorDot color={ORANGE} /></span>
          </div>
          <p className="mt-3 text-body-sm text-muted-foreground">Gols: Leal 2, Rafa · Bruno, Vini</p>
        </div>
      )}

      {stage === "final" && (
        <div className="mt-5 space-y-2">
          <div className="flex items-center justify-between rounded-tile bg-highlight-muted px-3 py-2.5">
            <span className="text-body font-medium">Campeão</span>
            <span className="flex items-center gap-2 text-body font-semibold"><TeamColorDot color={BLUE} />Time Azul</span>
          </div>
          <div className="flex items-center justify-between rounded-tile bg-card-elevated px-3 py-2.5">
            <span className="text-body font-medium">Artilheiro</span>
            <span className="text-body font-semibold text-highlight-text">Leal · 4 gols</span>
          </div>
        </div>
      )}
    </div>
  )
}
