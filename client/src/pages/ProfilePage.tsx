import { useState } from "react"
import { PageContainer } from "@/components/layout/PageContainer"
import { Link, useParams, useSearchParams } from "react-router-dom"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { AwardsCard } from "@/components/profile/AwardsCard"
import EditProfileModal from "@/components/profile/EditProfileModal"
import { EvolutionChart } from "@/components/profile/EvolutionChart"
import { MatchHistoryTable } from "@/components/profile/MatchHistoryTable"
import { PerformanceCard } from "@/components/profile/PerformanceCard"
import { ProfileHeader } from "@/components/profile/ProfileHeader"
import { ProfileKpiStrip } from "@/components/profile/ProfileKpiStrip"
import { ProfilePeladasList } from "@/components/profile/ProfilePeladasList"
import { ProfileSkeleton } from "@/components/profile/ProfileSkeleton"
import { RecentForm } from "@/components/profile/RecentForm"
import { useProfile } from "@/components/profile/hooks/useProfile"
import { useAuth } from "@/hooks/useAuth"

type ProfileTab = "visao" | "historico" | "peladas"

const TAB_LABEL: Record<ProfileTab, string> = {
  visao: "Visão geral",
  historico: "Histórico",
  peladas: "Peladas",
}
const TABS = Object.keys(TAB_LABEL) as ProfileTab[]

const isProfileTab = (value: string | null): value is ProfileTab => TABS.includes(value as ProfileTab)

export default function ProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { user: currentUser } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const [editOpen, setEditOpen] = useState(false)
  const {
    status, profile, stats, setProfile,
    peladas, peladasLoading,
    timelinePoints, timelineLoading,
    matchHistory, matchHistoryLoading,
  } = useProfile(Number(id))

  // the tab lives in the URL (?aba=historico), so reload, back/forward and shared links keep it
  const requested = searchParams.get("aba")
  const tab: ProfileTab = isProfileTab(requested) ? requested : "visao"
  function changeTab(value: string) {
    if (!isProfileTab(value)) return
    setSearchParams(value === "visao" ? {} : { aba: value })
  }

  if (status === "loading") return <ProfileSkeleton />

  if (!profile || !stats) {
    if (status === "error") {
      return (
        <PageContainer className="page-enter">
          <Alert variant="destructive">
            <AlertDescription>Não foi possível carregar o perfil.</AlertDescription>
          </Alert>
        </PageContainer>
      )
    }
    return (
      <div className="page-enter flex flex-col items-center justify-center gap-2 px-4 py-24 text-center">
        <h1 className="text-xl font-semibold">{status === "forbidden" ? "Perfil restrito" : "Perfil não encontrado"}</h1>
        <p className="text-muted-foreground">
          {status === "forbidden"
            ? "Você só pode ver o perfil de jogadores das suas peladas."
            : "Este jogador não existe ou foi removido."}
        </p>
        <Button variant="outline" className="mt-4" asChild>
          <Link to="/home">Voltar ao início</Link>
        </Button>
      </div>
    )
  }

  const isOwnProfile = currentUser?.id === profile.id

  return (
    <PageContainer className="page-enter gap-5">
      <ProfileHeader
        profile={profile}
        isOwnProfile={isOwnProfile}
        peladaCount={peladasLoading ? null : peladas.length}
        onEdit={() => setEditOpen(true)}
      />

      <ProfileKpiStrip stats={stats} />

      <Tabs value={tab} onValueChange={changeTab} className="flex flex-col gap-3">
        <TabsList variant="pill" aria-label="Seções do perfil" className="md:self-start">
          {TABS.map((value) => (
            <TabsTrigger key={value} value={value} variant="pill">
              {TAB_LABEL[value]}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value="visao" className="mt-0 grid gap-4 lg:grid-cols-3">
          <div className="lg:col-span-3">
            <RecentForm matchHistory={matchHistory} loading={matchHistoryLoading} />
          </div>
          <PerformanceCard stats={stats} />
          <div className="min-w-0 lg:col-span-2">
            <EvolutionChart timelinePoints={timelinePoints} loading={timelineLoading} />
          </div>
          <div className="lg:col-span-3">
            <AwardsCard stats={stats} />
          </div>
        </TabsContent>

        <TabsContent value="historico" className="mt-0">
          <MatchHistoryTable matchHistory={matchHistory} loading={matchHistoryLoading} />
        </TabsContent>

        <TabsContent value="peladas" className="mt-0">
          <ProfilePeladasList peladas={peladas} loading={peladasLoading} isOwnProfile={isOwnProfile} />
        </TabsContent>
      </Tabs>

      {editOpen && (
        <EditProfileModal profile={profile} onClose={() => setEditOpen(false)} onProfileUpdated={setProfile} />
      )}
    </PageContainer>
  )
}
