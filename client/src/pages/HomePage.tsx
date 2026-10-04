import { useMemo, type ReactNode } from "react"
import { Link } from "react-router-dom"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { HomeBanner } from "@/components/home/HomeBanner"
import { UpcomingSessionCard } from "@/components/home/UpcomingSessionCard"
import { CreatePeladaCard, MyPeladaCard } from "@/components/home/MyPeladaCard"
import { useUserStats } from "@/components/home/hooks/useUserStats"
import { useUpcomingAttendance } from "@/components/home/hooks/useUpcomingAttendance"
import { useAuth } from "@/hooks/useAuth"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { statsKpis, upcomingSessions } from "@/utils/home"

// Mobile: horizontal snap carousel bleeding to the screen edge (~30% of the next card visible); md+: a grid
const carousel =
  "-mx-4 grid snap-x snap-mandatory scroll-px-4 auto-cols-[calc((100%-44px)/1.3)] grid-flow-col gap-3 overflow-x-auto px-4 pb-1 scrollbar-none md:mx-0 md:snap-none md:auto-cols-auto md:grid-flow-row md:overflow-visible md:px-0 md:pb-0"

function SectionTitle({ title, extra, action }: { title: string; extra: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-2.5">
      <div className="flex items-baseline gap-2.5">
        <h2 className="text-[17px] font-semibold">{title}</h2>
        <span className="text-[13px] text-subtle-foreground">{extra}</span>
      </div>
      {action}
    </div>
  )
}

export default function HomePage() {
  const { user } = useAuth()
  const { peladas, loading, error, reload, openCreatePelada } = useMyPeladasContext()
  const { stats, loading: statsLoading, error: statsError } = useUserStats(user?.id ?? null)
  const { pendingId, toggle } = useUpcomingAttendance()

  const upcoming = useMemo(() => upcomingSessions(peladas), [peladas])
  const pendingCount = upcoming.filter((p) => !p.nextDaily.isConfirmed).length

  return (
    <div className="page-enter flex flex-1 flex-col">
      <main className="mx-auto flex w-full max-w-[1120px] flex-col gap-9 px-4 py-5 md:p-8">
        <HomeBanner username={user?.username ?? ""} pendingCount={pendingCount} onCreatePelada={openCreatePelada} />

        {error && (
          <Alert variant="destructive" className="flex items-center justify-between gap-4">
            <AlertDescription>Não foi possível carregar suas peladas.</AlertDescription>
            <Button variant="outline" size="sm" onClick={reload}>
              Tentar novamente
            </Button>
          </Alert>
        )}

        <section className="flex flex-col gap-3.5">
          <SectionTitle title="Próximas sessões" extra={loading ? "" : upcoming.length} />
          {loading ? (
            <div className={`${carousel} md:grid-cols-[repeat(auto-fill,minmax(300px,1fr))]`}>
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-[248px] rounded-tile" />
              ))}
            </div>
          ) : upcoming.length === 0 ? (
            <div className="rounded-tile border border-dashed border-input p-8 text-center text-sm text-subtle-foreground">
              Nenhuma sessão agendada nas suas peladas.
            </div>
          ) : (
            <div className={`${carousel} md:grid-cols-[repeat(auto-fill,minmax(300px,1fr))]`}>
              {upcoming.map((pelada) => (
                <UpcomingSessionCard
                  key={pelada.id}
                  pelada={pelada}
                  next={pelada.nextDaily}
                  pending={pendingId === pelada.nextDaily.id}
                  onToggle={() => toggle(pelada.id, pelada.nextDaily)}
                />
              ))}
            </div>
          )}
        </section>

        <section className="flex flex-col gap-3.5">
          <SectionTitle
            title="Seus números"
            extra="em todas as peladas"
            action={
              user && (
                <Link to={`/profile/${user.id}`} className="text-[13px] text-muted-foreground transition-colors hover:text-foreground">
                  Ver perfil →
                </Link>
              )
            }
          />
          {statsError ? (
            <p className="text-sm text-subtle-foreground">Não foi possível carregar seus números.</p>
          ) : (
            <dl className="grid grid-cols-[repeat(auto-fit,minmax(150px,1fr))] gap-3">
              {statsLoading || !stats
                ? [0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-[94px] rounded-xl" />)
                : statsKpis(stats).map((kpi) => (
                    <div key={kpi.label} className="flex flex-col gap-1 rounded-xl border bg-card px-4 py-3.5">
                      <dt className="text-xs text-muted-foreground">{kpi.label}</dt>
                      <dd className="text-[26px] font-bold tabular-nums">{kpi.value}</dd>
                      <dd className="text-xs text-faint-foreground">{kpi.sub}</dd>
                    </div>
                  ))}
            </dl>
          )}
        </section>

        <section className="flex flex-col gap-3.5">
          <SectionTitle title="Minhas peladas" extra={loading ? "" : peladas.length} />
          <div className={`${carousel} md:grid-cols-[repeat(auto-fill,minmax(240px,1fr))]`}>
            {loading
              ? [0, 1, 2].map((i) => <Skeleton key={i} className="h-[214px] rounded-tile" />)
              : peladas.map((pelada) => <MyPeladaCard key={pelada.id} pelada={pelada} />)}
            {!loading && <CreatePeladaCard onClick={openCreatePelada} />}
          </div>
        </section>
      </main>
    </div>
  )
}
