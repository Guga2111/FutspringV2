import { Plus } from "lucide-react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import type { DailyDetail } from "@/types/daily"

interface LiveSessionCardProps {
  daily: DailyDetail
  onEnterResults: () => void
  onFinalize: () => void
}

function matchesTitle(count: number): string {
  if (count === 0) return "Nenhuma partida lançada"
  return count === 1 ? "1 partida lançada" : `${count} partidas lançadas`
}

export default function LiveSessionCard({ daily, onEnterResults, onFinalize }: LiveSessionCardProps) {
  const count = daily.matches.length
  return (
    <section className="bg-gradient-live flex flex-wrap items-center gap-5 rounded-tile border border-success-border p-5">
      <div className="flex min-w-[240px] flex-1 flex-col gap-1.5">
        <span className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.06em] text-success">
          <span className="size-2 rounded-full bg-success-strong shadow-[0_0_0_4px_hsl(var(--success-strong)/0.18)]" />
          Sessão ao vivo
        </span>
        <span className="text-[22px] font-bold">{matchesTitle(count)}</span>
        <span className="max-w-[520px] text-[13px] text-live-foreground">
          Lance os placares conforme os jogos acabam. No fim, finalize a sessão para calcular prêmios, ranking e estatísticas.
        </span>
      </div>
      {daily.isAdmin && (
        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            disabled={count === 0}
            onClick={onFinalize}
            className={cn("h-10 border-live-border bg-transparent px-4 hover:bg-success-muted", count === 0 && "text-live-foreground/60")}
          >
            Finalizar sessão
          </Button>
          <Button variant="gradient" onClick={onEnterResults} className="h-10 gap-2 px-[18px] font-semibold">
            <Plus className="size-[15px]" strokeWidth={2.2} />
            Lançar resultados
          </Button>
        </div>
      )}
    </section>
  )
}
