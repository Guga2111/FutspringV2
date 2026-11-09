import { Link } from "react-router-dom"
import { usePeladaWorkspace } from "@/components/pelada/hooks/usePeladaWorkspace"
import { usePeladaSubscription } from "@/components/subscription/hooks/usePeladaSubscription"
import { SubscriptionCheckout } from "@/components/subscription/SubscriptionCheckout"
import { SubscriptionPaymentsList } from "@/components/subscription/SubscriptionPaymentsList"
import { SubscriptionStatusCard } from "@/components/subscription/SubscriptionStatusCard"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { areaPath } from "@/utils/peladaAreas"

// The creator's Assinatura area: the state, the plans with their Pix and the payments
export default function PeladaSubscriptionPage() {
  const w = usePeladaWorkspace()
  const { data, loading, accessDenied, error, retry, setData } = usePeladaSubscription(w.pelada.id, w.isCreator)

  // Hidden from everyone else in the navigation; a direct link gets this state (the API refuses anyway)
  if (!w.isCreator || accessDenied) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 py-24 text-center">
        <h2 className="text-xl font-semibold">Acesso negado</h2>
        <p className="text-muted-foreground">Apenas o criador cuida da assinatura da pelada.</p>
        <Button variant="outline" className="mt-4" asChild>
          <Link to={areaPath(w.pelada.id, "overview")}>Voltar para a pelada</Link>
        </Button>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="flex flex-col gap-6">
        <Skeleton className="h-[92px] w-full rounded-tile" />
        <div className="grid gap-3 md:grid-cols-2">
          <Skeleton className="h-[78px] rounded-xl" />
          <Skeleton className="h-[78px] rounded-xl" />
        </div>
        <Skeleton className="h-[200px] w-full rounded-tile" />
      </div>
    )
  }

  if (error || !data) {
    return (
      <Alert variant="destructive" className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <AlertDescription>Não foi possível carregar a assinatura.</AlertDescription>
        <Button variant="outline" size="sm" onClick={retry}>
          Tentar de novo
        </Button>
      </Alert>
    )
  }

  return (
    <div className="flex flex-col gap-8">
      <SubscriptionStatusCard subscription={data} />
      {data.status !== "EXEMPT" && <SubscriptionCheckout peladaId={w.pelada.id} subscription={data} onClaimed={setData} />}
      <SubscriptionPaymentsList payments={data.payments} />
    </div>
  )
}
