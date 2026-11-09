import { useCallback, useState } from "react"
import { Navigate, useSearchParams } from "react-router-dom"
import { getPlatformPeladas, PLATFORM_PAGE_SIZE } from "@/api/platform"
import { ListPagination } from "@/components/ListPagination"
import { PageContainer } from "@/components/layout/PageContainer"
import { PeladasTable } from "@/components/platform/PeladasTable"
import { PlatformLoadError } from "@/components/platform/PlatformLoadError"
import { PlatformPageHeader } from "@/components/platform/PlatformPageHeader"
import { PlatformSearch } from "@/components/platform/PlatformSearch"
import { useDebouncedValue, usePageOf, usePlatformRequest } from "@/components/platform/hooks/usePlatformRequest"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { subscriptionStatusLabel, type SubscriptionStatus } from "@/types/subscription"

const STATES = Object.keys(subscriptionStatusLabel) as SubscriptionStatus[]
const isState = (value: string | null): value is SubscriptionStatus => STATES.includes(value as SubscriptionStatus)

export default function AdminPeladasPage() {
  // the state lives in the URL, so the overview links to "?estado=PAST_DUE"
  const [params, setParams] = useSearchParams()
  const raw = params.get("estado")
  const state = isState(raw) ? raw : undefined
  const [query, setQuery] = useState("")
  const q = useDebouncedValue(query.trim())
  const [page, setPage] = usePageOf(`${state}:${q}`)
  const fetcher = useCallback(() => getPlatformPeladas(page, { state, q }), [page, state, q])
  const { data, loading, error, notFound, retry } = usePlatformRequest(fetcher, "Não foi possível carregar as peladas")

  if (notFound) return <Navigate to="/404" replace />
  return (
    <PageContainer className="page-enter">
      <PlatformPageHeader area="peladas" title="Peladas" />
      <div className="flex flex-col gap-3 md:flex-row md:items-center">
        <PlatformSearch value={query} onChange={setQuery} label="Buscar pelada ou criador" />
        <ToggleGroup
          type="single"
          variant="chip"
          size="chip"
          value={state ?? ""}
          onValueChange={(value) => setParams(value ? { estado: value } : {}, { replace: true })}
          aria-label="Filtrar por estado"
          className="flex-wrap justify-start gap-1.5"
        >
          <ToggleGroupItem value="">Todas</ToggleGroupItem>
          {STATES.map((value) => (
            <ToggleGroupItem key={value} value={value}>
              {subscriptionStatusLabel[value]}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
      </div>
      {error && <PlatformLoadError message={error} onRetry={retry} />}
      {loading && <Skeleton className="h-[320px] w-full" />}
      {data &&
        (data.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nenhuma pelada encontrada.</p>
        ) : (
          <>
            <PeladasTable items={data.items} />
            <ListPagination page={page} total={data.total} size={PLATFORM_PAGE_SIZE} noun={["pelada", "peladas"]} onPage={setPage} />
          </>
        ))}
    </PageContainer>
  )
}
