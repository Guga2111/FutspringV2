import { useCallback } from "react"
import { Navigate } from "react-router-dom"
import { getPlatformOverview } from "@/api/platform"
import { PageContainer } from "@/components/layout/PageContainer"
import { AttentionList } from "@/components/platform/AttentionList"
import { OverviewKpis } from "@/components/platform/OverviewKpis"
import { PlatformLoadError } from "@/components/platform/PlatformLoadError"
import { PlatformPageHeader } from "@/components/platform/PlatformPageHeader"
import { RevenueChart } from "@/components/platform/RevenueChart"
import { usePlatformRequest } from "@/components/platform/hooks/usePlatformRequest"
import { Skeleton } from "@/components/ui/skeleton"

export default function AdminOverviewPage() {
  const fetcher = useCallback(() => getPlatformOverview(), [])
  const { data, loading, error, notFound, retry } = usePlatformRequest(fetcher, "Não foi possível carregar o painel")

  // the panel answers 404 to anyone who isn't a platform admin: "Bola fora" (/404 has no route of its own)
  if (notFound) return <Navigate to="/404" replace />
  return (
    <PageContainer className="page-enter">
      <PlatformPageHeader area="overview" title="Painel da plataforma" />
      {error && <PlatformLoadError message={error} onRetry={retry} />}
      {loading && (
        <div className="flex flex-col gap-6">
          <Skeleton className="h-[104px] w-full" />
          <div className="grid gap-6 lg:grid-cols-2">
            <Skeleton className="h-[220px]" />
            <Skeleton className="h-[300px]" />
          </div>
        </div>
      )}
      {data && (
        <>
          <OverviewKpis overview={data} />
          <div className="grid gap-6 lg:grid-cols-2">
            <AttentionList overview={data} />
            <RevenueChart months={data.revenueByMonth} />
          </div>
        </>
      )}
    </PageContainer>
  )
}
