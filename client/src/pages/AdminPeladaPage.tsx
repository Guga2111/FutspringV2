import { useCallback, useState } from "react"
import { Link, Navigate, useParams } from "react-router-dom"
import { getPlatformPelada } from "@/api/platform"
import { PageContainer } from "@/components/layout/PageContainer"
import { PlatformLoadError } from "@/components/platform/PlatformLoadError"
import { PlatformPageHeader } from "@/components/platform/PlatformPageHeader"
import { PlatformPaymentsList } from "@/components/platform/PlatformPaymentsList"
import { RecordPaymentSheet } from "@/components/platform/RecordPaymentSheet"
import { SubscriptionActionDialog } from "@/components/platform/SubscriptionActionDialog"
import { SubscriptionCard } from "@/components/platform/SubscriptionCard"
import { SubscriptionEventList } from "@/components/platform/SubscriptionEventList"
import { VoidPaymentDialog } from "@/components/platform/VoidPaymentDialog"
import { usePlatformPeladaActions } from "@/components/platform/hooks/usePlatformPeladaActions"
import { usePlatformRequest } from "@/components/platform/hooks/usePlatformRequest"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import type { SubscriptionFormAction } from "@/schemas/platform"
import type { PlatformPayment } from "@/types/platform"
import { formatIsoDate } from "@/utils/platform"

export default function AdminPeladaPage() {
  const id = Number(useParams().id)
  const fetcher = useCallback(() => getPlatformPelada(id), [id])
  const { data, loading, error, notFound, retry, setData } = usePlatformRequest(fetcher, "Não foi possível carregar a pelada")
  const actions = usePlatformPeladaActions(id, setData)
  const [paying, setPaying] = useState(false)
  const [action, setAction] = useState<SubscriptionFormAction | null>(null)
  const [voiding, setVoiding] = useState<PlatformPayment | null>(null)

  if (notFound || !Number.isInteger(id)) return <Navigate to="/404" replace />
  const pelada = data?.pelada
  return (
    <PageContainer className="page-enter">
      <PlatformPageHeader
        area="peladas"
        title={pelada?.name ?? "Pelada"}
        trail={[{ label: pelada?.name ?? "Pelada", truncate: true }]}
        description={
          pelada && (
            <>
              Criada por{" "}
              <Link to={`/profile/${pelada.creator.id}`} className="font-medium text-foreground underline-offset-4 hover:underline">
                {pelada.creator.username}
              </Link>{" "}
              ({pelada.creatorEmail}) · {pelada.memberCount} membros · última sessão{" "}
              {pelada.lastSessionDate ? formatIsoDate(pelada.lastSessionDate) : "nenhuma"}
            </>
          )
        }
      />
      {error && <PlatformLoadError message={error} onRetry={retry} />}
      {loading && (
        <div className="flex flex-col gap-6">
          <Skeleton className="h-[260px] w-full" />
          <Skeleton className="h-[200px] w-full" />
        </div>
      )}
      {data && (
        <>
          {data.subscription ? (
            <SubscriptionCard
              subscription={data.subscription}
              savingFounder={actions.savingFounder}
              onRecordPayment={() => setPaying(true)}
              onAction={setAction}
              onFounderChange={actions.setFounder}
            />
          ) : (
            <Alert>
              <AlertDescription>Esta pelada não tem assinatura registrada.</AlertDescription>
            </Alert>
          )}
          <div className="grid gap-6 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-title">Pagamentos</CardTitle>
              </CardHeader>
              <CardContent>
                <PlatformPaymentsList payments={data.payments} onVoid={setVoiding} />
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle className="text-title">Histórico</CardTitle>
              </CardHeader>
              <CardContent>
                <SubscriptionEventList events={data.events} />
              </CardContent>
            </Card>
          </div>
          {paying && data.subscription && (
            <RecordPaymentSheet subscription={data.subscription} onSubmit={actions.recordPayment} onClose={() => setPaying(false)} />
          )}
          {action && <SubscriptionActionDialog action={action} onSubmit={actions.changeSubscription} onClose={() => setAction(null)} />}
          {voiding && (
            <VoidPaymentDialog
              payment={voiding}
              onSubmit={(reason) => actions.voidPayment(voiding.id, reason)}
              onClose={() => setVoiding(null)}
            />
          )}
        </>
      )}
    </PageContainer>
  )
}
