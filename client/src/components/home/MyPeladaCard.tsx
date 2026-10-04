import { Link } from "react-router-dom"
import { Plus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { dayOfWeekLabel } from "@/lib/constants"
import { formatNextSessionShort } from "@/utils/dates"
import type { PeladaResponse } from "@/types/pelada"

export function MyPeladaCard({ pelada }: { pelada: PeladaResponse }) {
  return (
    <Link
      to={`/pelada/${pelada.id}`}
      className="flex snap-start flex-col overflow-hidden rounded-tile border bg-card transition-colors hover:border-input"
    >
      <PeladaAvatar
        name={pelada.name}
        image={pelada.image}
        objectPosition="center 22%"
        className="h-[110px] w-full rounded-none text-[28px] font-extrabold text-white/60"
      />
      <div className="flex flex-col gap-1.5 p-3.5">
        <span className="text-[15px] font-semibold">{pelada.name}</span>
        <span className="text-[13px] text-muted-foreground">
          {dayOfWeekLabel(pelada.dayOfWeek)} · {pelada.timeOfDay}
        </span>
        <div className="flex flex-wrap items-center gap-2 pt-1">
          <Badge variant="status" className="bg-secondary px-2 py-0.5 text-xs font-semibold text-foreground">
            {pelada.memberCount} membros
          </Badge>
          {pelada.isAdmin && (
            <Badge variant="status" className="bg-status-live px-2 py-0.5 text-xs font-semibold text-status-live-foreground">
              Admin
            </Badge>
          )}
          <span className="text-xs text-subtle-foreground">
            {pelada.nextDaily ? `Próxima: ${formatNextSessionShort(pelada.nextDaily)}` : "Sem sessão marcada"}
          </span>
        </div>
      </div>
    </Link>
  )
}

export function CreatePeladaCard({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-[200px] snap-start flex-col items-center justify-center gap-2.5 rounded-tile border border-dashed border-input text-muted-foreground transition-colors hover:border-faint-foreground hover:bg-card hover:text-foreground"
    >
      <span className="flex size-10 items-center justify-center rounded-full border border-star-off">
        <Plus className="size-[18px]" />
      </span>
      <span className="text-sm font-medium">Criar nova pelada</span>
    </button>
  )
}
