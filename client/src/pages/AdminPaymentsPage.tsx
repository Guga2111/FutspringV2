import { useCallback, useState } from "react"
import { Navigate } from "react-router-dom"
import { format } from "date-fns"
import { getPlatformPayments, PLATFORM_PAGE_SIZE } from "@/api/platform"
import { DateRangeFilter } from "@/components/DateRangeFilter"
import { ListPagination } from "@/components/ListPagination"
import { PageContainer } from "@/components/layout/PageContainer"
import { PlatformLoadError } from "@/components/platform/PlatformLoadError"
import { PlatformPageHeader } from "@/components/platform/PlatformPageHeader"
import { PlatformPaymentsList } from "@/components/platform/PlatformPaymentsList"
import { usePageOf, usePlatformRequest } from "@/components/platform/hooks/usePlatformRequest"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import type { DateRange } from "@/utils/dates"
import { paymentsSummary } from "@/utils/platform"

const iso = (date: Date | undefined) => (date ? format(date, "yyyy-MM-dd") : undefined)

export default function AdminPaymentsPage() {
  const [range, setRange] = useState<DateRange | undefined>()
  const from = iso(range?.from)
  // a single picked day is that day
  const to = iso(range?.to ?? range?.from)
  const [page, setPage] = usePageOf(`${from}:${to}`)
  const fetcher = useCallback(() => getPlatformPayments(page, { from, to }), [page, from, to])
  const { data, loading, error, notFound, retry } = usePlatformRequest(fetcher, "Não foi possível carregar os pagamentos")

  if (notFound) return <Navigate to="/404" replace />
  return (
    <PageContainer className="page-enter">
      <PlatformPageHeader area="payments" title="Pagamentos" description="O total do período deixa os estornados de fora." />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="font-display text-heading font-bold tabular-nums" aria-live="polite">
          {data ? paymentsSummary(data.total, data.sum) : " "}
        </p>
        <DateRangeFilter value={range} onChange={setRange} />
      </div>
      {error && <PlatformLoadError message={error} onRetry={retry} />}
      {loading && <Skeleton className="h-[280px] w-full" />}
      {data && (
        <>
          <Card>
            <CardContent className="pt-6">
              <PlatformPaymentsList payments={data.items} showPelada />
            </CardContent>
          </Card>
          <ListPagination page={page} total={data.total} size={PLATFORM_PAGE_SIZE} noun={["pagamento", "pagamentos"]} onPage={setPage} />
        </>
      )}
    </PageContainer>
  )
}
