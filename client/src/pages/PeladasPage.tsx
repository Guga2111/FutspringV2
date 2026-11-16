import { Link } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowRight01Icon, PlusSignIcon } from "@hugeicons/core-free-icons"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { PageContainer } from "@/components/layout/PageContainer"
import { PageHeader } from "@/components/layout/PageHeader"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { useRefresh } from "@/hooks/useMobileShell"
import type { PeladaResponse } from "@/types/pelada"
import { formatNextSessionShort } from "@/utils/dates"

const rowClass = "flex min-h-[72px] items-center gap-3 rounded-tile px-3 py-2.5 transition-colors hover:bg-row-hover"

function PeladaRow({ pelada }: { pelada: PeladaResponse }) {
  const next = pelada.nextDaily
  return (
    <li>
      <Link
        to={`/pelada/${pelada.id}`}
        className={`${rowClass} focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring`}
      >
        <PeladaAvatar name={pelada.name} image={pelada.image} className="size-12 shrink-0 rounded-[12px] text-sm" />
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="flex min-w-0 items-center gap-2">
            <span className="truncate text-body-lg font-semibold">{pelada.name}</span>
            {pelada.isAdmin && (
              <Badge variant="status" className="shrink-0 bg-brand-muted px-2 py-0.5 text-xs font-semibold text-brand-text">
                Admin
              </Badge>
            )}
          </span>
          <span className="flex min-w-0 gap-1 text-body-sm text-muted-foreground">
            <span className="truncate">{formatNextSessionShort(next)}</span>
            {next && (
              // the spots never truncate: the date gives way first
              <span className="shrink-0 tabular-nums">
                <span aria-hidden>· {next.confirmedCount}/{next.capacity}</span>
                <span className="sr-only">, {next.confirmedCount} de {next.capacity} vagas confirmadas</span>
              </span>
            )}
          </span>
        </span>
        <HugeiconsIcon icon={ArrowRight01Icon} className="size-5 shrink-0 text-faint-foreground" />
      </Link>
    </li>
  )
}

// /peladas: the caller's peladas as a list of conversations (the "Peladas" tab of the mobile shell; it works at any
// width, but the desktop's sidebar already lists them). Data from MyPeladasContext, no request of its own.
export default function PeladasPage() {
  const { peladas, loading, error, reload, openCreatePelada } = useMyPeladasContext()
  useRefresh(reload)

  return (
    <div className="page-enter flex flex-1 flex-col">
      <PageContainer width="narrow">
        <PageHeader breadcrumb={[{ label: "Início", to: "/home" }, { label: "Peladas" }]} title="Peladas" />

        {error && (
          <Alert variant="destructive">
            <AlertDescription className="flex flex-col items-start gap-3">
              Não foi possível carregar suas peladas.
              <Button variant="outline" size="sm" onClick={() => void reload()}>
                Tentar novamente
              </Button>
            </AlertDescription>
          </Alert>
        )}

        {loading ? (
          <div className="flex flex-col gap-1" aria-hidden>
            {[0, 1, 2].map((i) => (
              <div key={i} className="flex items-center gap-3 px-3 py-2.5">
                <Skeleton className="size-12 rounded-[12px]" />
                <div className="flex flex-1 flex-col gap-2">
                  <Skeleton className="h-4 w-2/3" />
                  <Skeleton className="h-3.5 w-1/2" />
                </div>
              </div>
            ))}
          </div>
        ) : (
          <>
            {peladas.length === 0 && !error && (
              <p className="px-3 text-body text-muted-foreground">
                Você ainda não está em nenhuma pelada. Crie a sua ou entre pelo link de convite de um amigo.
              </p>
            )}
            <ul className="-mx-3 flex flex-col gap-0.5" aria-label="Suas peladas">
              {peladas.map((pelada) => (
                <PeladaRow key={pelada.id} pelada={pelada} />
              ))}
              <li>
                <Button
                  variant="ghost"
                  onClick={openCreatePelada}
                  className="h-auto min-h-[72px] w-full justify-start gap-3 rounded-tile px-3 py-2.5 text-left font-normal"
                >
                  <span className="flex size-12 shrink-0 items-center justify-center rounded-[12px] border border-dashed border-input text-muted-foreground [&_svg]:size-5">
                    <HugeiconsIcon icon={PlusSignIcon} />
                  </span>
                  <span className="text-body-lg font-semibold">Criar pelada</span>
                </Button>
              </li>
            </ul>
          </>
        )}
      </PageContainer>
    </div>
  )
}
