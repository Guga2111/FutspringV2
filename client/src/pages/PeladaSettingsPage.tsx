import type { ReactNode } from "react"
import { Link, useParams } from "react-router-dom"
import { AutomationCard } from "@/components/pelada-settings/AutomationCard"
import { DangerZone } from "@/components/pelada-settings/DangerZone"
import { usePeladaSettings } from "@/components/pelada-settings/hooks/usePeladaSettings"
import { InfoCard } from "@/components/pelada-settings/InfoCard"
import { PeladaPageHeader } from "@/components/pelada/PeladaPageHeader"
import { LocationCard } from "@/components/pelada-settings/LocationCard"
import { ScheduleCard } from "@/components/pelada-settings/ScheduleCard"
import { SettingsNav, type SettingsSection } from "@/components/pelada-settings/SettingsNav"
import { SlotsCard } from "@/components/pelada-settings/SlotsCard"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/hooks/useAuth"
import { areaPath } from "@/utils/peladaAreas"

const SECTIONS: SettingsSection[] = [
  { id: "informacoes", label: "Informações" },
  { id: "horario", label: "Horário" },
  { id: "local", label: "Local" },
  { id: "vagas", label: "Vagas" },
  { id: "automacao", label: "Automação" },
  { id: "transferir", label: "Transferir pelada" },
  { id: "excluir", label: "Excluir pelada" },
]

export default function PeladaSettingsPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const { pelada, nextDaily, loading, accessDenied, error, merge, retry } = usePeladaSettings(id)

  const isAdmin = pelada?.members.find((m) => m.id === user?.id)?.isAdmin ?? false
  const isCreator = pelada != null && user != null && pelada.creatorId === user.id

  let content: ReactNode
  if (loading) {
    content = <SettingsSkeleton />
  } else if (accessDenied || (pelada && !isAdmin)) {
    content = (
      <div className="flex flex-col items-center justify-center gap-2 py-24 text-center">
        <h2 className="text-xl font-semibold">Acesso negado</h2>
        <p className="text-muted-foreground">
          {accessDenied ? "Você não faz parte desta pelada." : "Apenas os admins podem alterar as configurações da pelada."}
        </p>
        {pelada && (
          <Button variant="outline" className="mt-4" asChild>
            <Link to={areaPath(pelada.id, "overview")}>Voltar para a pelada</Link>
          </Button>
        )}
      </div>
    )
  } else if (error || !pelada) {
    content = (
      <Alert variant="destructive" className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <AlertDescription>Não foi possível carregar a pelada.</AlertDescription>
        <Button variant="outline" size="sm" onClick={retry}>
          Tentar de novo
        </Button>
      </Alert>
    )
  } else {
    // key: after a transfer or another pelada, the forms start from the saved values
    content = (
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_200px]">
        <div className="flex min-w-0 flex-col gap-8" key={pelada.id}>
          <SettingsGroup label="Pelada">
            <InfoCard pelada={pelada} onSaved={merge} />
            <ScheduleCard pelada={pelada} onSaved={merge} />
            <LocationCard pelada={pelada} onSaved={merge} />
          </SettingsGroup>
          <SettingsGroup label="Sessões">
            <SlotsCard pelada={pelada} nextDaily={nextDaily} onSaved={merge} />
            <AutomationCard pelada={pelada} onSaved={merge} />
          </SettingsGroup>
          <SettingsGroup label="Zona de perigo">
            <DangerZone pelada={pelada} isCreator={isCreator} onTransferred={merge} />
          </SettingsGroup>
        </div>
        <aside className="hidden lg:block">
          <div className="sticky top-6">
            <SettingsNav sections={SECTIONS} />
          </div>
        </aside>
      </div>
    )
  }

  return (
    <div className="page-enter flex flex-1 flex-col">
      <main className="mx-auto flex w-full max-w-[1120px] flex-col gap-6 px-4 py-5 md:px-8 md:py-7">
        {id && (
          <PeladaPageHeader
            peladaId={id}
            peladaName={pelada?.name ?? null}
            area="settings"
            isAdmin={isAdmin}
            title="Configurações"
            description="Gerencie as informações, o horário e as vagas da pelada. Preferências são salvas automaticamente; os demais blocos têm botão próprio."
          />
        )}
        {content}
      </main>
    </div>
  )
}

function SettingsGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-xs font-semibold uppercase tracking-wider text-faint-foreground">{label}</h2>
      <div className="flex flex-col gap-4">{children}</div>
    </section>
  )
}

// Shaped like the page: cards with a header, fields and a footer, and the index from lg
function SettingsSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_200px]">
      <div className="flex flex-col gap-4">
        <Skeleton className="h-3 w-16" />
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-56 w-full rounded-tile" />
        ))}
      </div>
      <div className="hidden flex-col gap-2 lg:flex">
        {[0, 1, 2, 3, 4].map((i) => (
          <Skeleton key={i} className="h-8 w-full rounded-md" />
        ))}
      </div>
    </div>
  )
}
