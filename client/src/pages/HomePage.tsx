import { useMemo, type ReactNode } from "react"
import { PageContainer } from "@/components/layout/PageContainer"
import { Link } from "react-router-dom"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { BumpAlert } from "@/components/BumpAlert"
import { PendingInvitations } from "@/components/invitations/PendingInvitations"
import { HomeBanner } from "@/components/home/HomeBanner"
import { EnablePushCard } from "@/components/home/EnablePushCard"
import { InstallAppCard } from "@/components/home/InstallAppCard"
import { UpcomingSessionCard } from "@/components/home/UpcomingSessionCard"
import { MyPeladaCard } from "@/components/home/MyPeladaCard"
import { useUserStats } from "@/components/home/hooks/useUserStats"
import { useUpcomingAttendance } from "@/components/home/hooks/useUpcomingAttendance"
import { useAuth } from "@/hooks/useAuth"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { cn } from "@/lib/utils"
import { statsKpis, upcomingSessions, type Kpi } from "@/utils/home"
import { useMyInvitationsContext } from "@/hooks/useMyInvitationsContext"
import { useRefresh } from "@/hooks/useMobileShell"

const KPI_TONE: Record<NonNullable<Kpi["tone"]>, string> = { highlight: "text-highlight-text", brand: "text-brand-text" }

// Keep the original swipe/snap carousel for peladas on phones; desktop remains a grid.
const peladaCarousel =
  "-mx-4 grid snap-x snap-mandatory scroll-px-4 auto-cols-[calc((100%-44px)/1.15)] grid-flow-col gap-3 overflow-x-auto px-4 pb-1 scrollbar-none md:mx-0 md:snap-none md:auto-cols-auto md:grid-flow-row md:overflow-visible md:px-0 md:pb-0"

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
  const { stats, loading: statsLoading, error: statsError, reload: reloadStats } = useUserStats(user?.id ?? null)
  const { reload: reloadInvitations, items: invitations, loading: invitationsLoading, error: invitationsError } = useMyInvitationsContext()
  const showInvitations = invitationsLoading || invitationsError || invitations.length > 0
  // pull to refresh: the peladas (and their next sessions), the invitations and "Seus números"
  useRefresh(() => Promise.all([reload(), reloadInvitations(), reloadStats()]))
  const { pendingId, toggle, dismissBump } = useUpcomingAttendance()

  const upcoming = useMemo(() => upcomingSessions(peladas), [peladas])
  const pendingCount = upcoming.filter((p) => !p.nextDaily.isConfirmed).length
  const bumped = upcoming.filter((p) => p.nextDaily.bump !== null)

  return (
    <div className="page-enter flex flex-1 flex-col">
      <PageContainer className="gap-9">
        <HomeBanner
          username={user?.username ?? ""}
          pendingCount={pendingCount}
          onCreatePelada={openCreatePelada}
        />

        {bumped.map((pelada) =>
          pelada.nextDaily.bump ? (
            <BumpAlert
              key={pelada.id}
              bump={pelada.nextDaily.bump}
              timeZone={pelada.timeZone}
              peladaName={pelada.name}
              pending={pendingId === pelada.nextDaily.id}
              onDismiss={() => dismissBump(pelada.id, pelada.nextDaily)}
            />
          ) : null,
        )}

        {error && (
          <Alert variant="destructive" className="flex items-center justify-between gap-4">
            <AlertDescription>Não foi possível carregar suas peladas.</AlertDescription>
            <Button variant="outline" size="sm" onClick={() => void reload()}>
              Tentar novamente
            </Button>
          </Alert>
        )}

        <div className={cn("grid grid-cols-1 items-start gap-6", showInvitations && "lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]")}>
          <section className="flex min-w-0 flex-col gap-3.5" aria-label="Próximas sessões">
            <SectionTitle title="Próximas sessões" extra={loading ? "" : upcoming.length} />
            {loading ? (
              <div className="grid grid-cols-1 gap-3 md:grid-cols-[repeat(auto-fit,minmax(300px,1fr))]">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="h-[230px] rounded-tile" />
                ))}
              </div>
            ) : upcoming.length === 0 ? (
              <div className="rounded-tile border border-dashed border-input p-8 text-center text-sm text-subtle-foreground">
                Nenhuma sessão agendada nas suas peladas.
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-3 md:grid-cols-[repeat(auto-fit,minmax(300px,1fr))]">
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

          <PendingInvitations />
        </div>

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
                      <dd className={cn("font-display text-score font-bold tabular-nums", kpi.tone && KPI_TONE[kpi.tone])}>{kpi.value}</dd>
                      <dd className="text-xs text-faint-foreground">{kpi.sub}</dd>
                    </div>
                  ))}
            </dl>
          )}
        </section>

        <section className="flex flex-col gap-3.5">
          <SectionTitle
            title="Minhas peladas"
            extra={loading ? "" : peladas.length}
            action={<Button variant="outline" size="sm" className="h-11" onClick={openCreatePelada}>Nova pelada</Button>}
          />
          <div className={cn(peladaCarousel, "md:grid-cols-[repeat(auto-fit,minmax(240px,1fr))]")}>
            {loading
              ? [0, 1, 2].map((i) => <Skeleton key={i} className="h-[214px] rounded-tile" />)
              : peladas.map((pelada) => <MyPeladaCard key={pelada.id} pelada={pelada} />)}
          </div>
        </section>
        <InstallAppCard />
        <EnablePushCard />
      </PageContainer>
    </div>
  )
}
