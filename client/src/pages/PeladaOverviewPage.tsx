import { useMemo } from "react"
import { Link } from "react-router-dom"
import { HugeiconsIcon, type IconSvgElement } from "@hugeicons/react"
import { ArrowRight01Icon, Award01Icon, Calendar03Icon, LeftToRightListNumberIcon, MailAdd01Icon, PlusSignIcon, Settings01Icon, UserGroupIcon, Wallet01Icon } from "@hugeicons/core-free-icons"
import { NextSessionCard } from "@/components/pelada/NextSessionCard"
import { PeladaBanner } from "@/components/pelada/PeladaBanner"
import { SessionRow } from "@/components/pelada/SessionHistoryList"
import { usePeladaWorkspace } from "@/components/pelada/hooks/usePeladaWorkspace"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { areaPath, PELADA_AREAS, visibleAreas, type PeladaArea } from "@/utils/peladaAreas"
import { pickNextSession, pickRecentSessions } from "@/utils/sessions"

const RECENT_LIMIT = 3

const AREA_ICONS: Record<Exclude<PeladaArea, "overview">, IconSvgElement> = {
  members: UserGroupIcon,
  sessions: Calendar03Icon,
  ranking: LeftToRightListNumberIcon,
  awards: Award01Icon,
  finance: Wallet01Icon,
  invitations: MailAdd01Icon,
  settings: Settings01Icon,
}

// What is happening now: the next session, the latest sessions, and a way into every area
export default function PeladaOverviewPage() {
  const w = usePeladaWorkspace()
  const nextSession = useMemo(() => pickNextSession(w.dailies), [w.dailies])
  const recent = useMemo(
    () => pickRecentSessions(w.dailies, nextSession?.id ?? null, RECENT_LIMIT),
    [w.dailies, nextSession],
  )
  const shortcuts = visibleAreas(w.isAdmin).filter((area): area is Exclude<PeladaArea, "overview"> => area !== "overview")
  const sessionsPath = areaPath(w.pelada.id, "sessions")

  return (
    <div className="flex flex-col gap-6">
      <PeladaBanner pelada={w.pelada} isCurrentUserAdmin={w.isAdmin} />

      {w.dailiesLoading ? (
        <Skeleton className="h-[116px] w-full rounded-tile" />
      ) : (
        <NextSessionCard
          daily={nextSession}
          timeZone={w.pelada.timeZone}
          isAdmin={w.isAdmin}
          attendancePending={w.attendancePending}
          onToggleAttendance={w.toggleAttendance}
        />
      )}

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
        <section className="flex min-w-0 flex-col gap-3" aria-labelledby="overview-recent">
          <div className="flex items-center justify-between gap-3">
            <h2 id="overview-recent" className="text-[15px] font-semibold">Atividade recente</h2>
            {recent.length > 0 && (
              <Button variant="link" className="h-auto p-0 text-[13px]" asChild>
                <Link to={sessionsPath}>Ver todas as sessões</Link>
              </Button>
            )}
          </div>
          {w.dailiesLoading ? (
            <Skeleton className="h-48 w-full rounded-xl" />
          ) : recent.length === 0 ? (
            <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-input p-8 text-center text-sm text-subtle-foreground">
              <span>Nenhuma sessão realizada ainda. Os resultados das sessões aparecem aqui.</span>
              {w.isAdmin && (
                <Button variant="outline" onClick={w.openCreateSession} className="h-[34px] gap-1.5 px-3.5">
                  <HugeiconsIcon icon={PlusSignIcon} className="size-3.5" strokeWidth={2.2} />
                  Nova sessão
                </Button>
              )}
            </div>
          ) : (
            <ul className="overflow-hidden rounded-xl border">
              {recent.map((daily) => (
                <SessionRow key={daily.id} daily={daily} />
              ))}
            </ul>
          )}
        </section>

        {/* No member activity endpoint yet: an honest placeholder, never invented entries */}
        <section className="flex flex-col gap-3" aria-labelledby="overview-members">
          <div className="flex items-center justify-between gap-3">
            <h2 id="overview-members" className="text-[15px] font-semibold">Mudanças de membros</h2>
            <Badge variant="secondary">Em breve</Badge>
          </div>
          <div className="flex flex-1 flex-col gap-3 rounded-xl bg-card-elevated p-5 text-sm text-muted-foreground">
            <p>Entradas, saídas e mudanças de tipo dos membros vão aparecer aqui.</p>
            <Button variant="outline" className="h-[34px] self-start px-3.5" asChild>
              <Link to={areaPath(w.pelada.id, "members")}>Ver membros</Link>
            </Button>
          </div>
        </section>
      </div>

      <section className="flex flex-col gap-3" aria-labelledby="overview-areas">
        <h2 id="overview-areas" className="text-[15px] font-semibold">Áreas da pelada</h2>
        <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {shortcuts.map((area) => {
            const Icon = AREA_ICONS[area]
            return (
              <li key={area}>
                <Link
                  to={areaPath(w.pelada.id, area)}
                  className="flex min-h-[60px] items-center gap-3 rounded-xl border bg-card px-4 py-3 transition-colors hover:bg-row-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <HugeiconsIcon icon={Icon} className="size-[18px] shrink-0 text-muted-foreground" />
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="text-sm font-medium">{PELADA_AREAS[area].label}</span>
                    <span className="truncate text-xs text-subtle-foreground">{PELADA_AREAS[area].description}</span>
                  </span>
                  <HugeiconsIcon icon={ArrowRight01Icon} className="size-4 shrink-0 text-faint-foreground" />
                </Link>
              </li>
            )
          })}
        </ul>
      </section>
    </div>
  )
}
