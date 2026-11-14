import { useEffect, useState } from "react"
import { Link, useParams } from "react-router-dom"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { getPelada } from "@/api/peladas"
import { reportFinancePayment } from "@/api/finance"
import { useAuth } from "@/hooks/useAuth"
import { useFinance } from "@/components/finance/hooks/useFinance"
import { PageContainer } from "@/components/layout/PageContainer"
import { PageHeader } from "@/components/layout/PageHeader"
import { Button } from "@/components/ui/button"
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Skeleton } from "@/components/ui/skeleton"
import { Collapsible, CollapsibleTrigger, CollapsibleContent } from "@/components/ui/collapsible"
import { FieldGroup, Field, FieldLabel, FieldDescription, FieldError } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { financeReportSchema } from "@/schemas/finance"
import { pendingFinanceNotice, formatFinanceMoney } from "@/utils/finance"
import { isWritable } from "@/utils/subscription"
import { getErrorMessage } from "@/lib/errors"
import { applyServerErrors } from "@/lib/form-errors"
import type { PeladaDetail } from "@/types/pelada"

const keepSelection = () => {}

export default function FinanceQuickReportPage() {
  const { id, listId } = useParams()
  const { user } = useAuth()
  const peladaId = Number(id), collectionId = Number(listId)
  if (!user || !Number.isSafeInteger(peladaId) || peladaId <= 0 || !Number.isSafeInteger(collectionId) || collectionId <= 0) {
    return <PageContainer width="narrow"><PageHeader breadcrumb={[]} title="Cobrança indisponível" /></PageContainer>
  }
  // Account and both route ids own all reads, form values and retry keys.
  return <QuickReport key={`${user.id}:${peladaId}:${collectionId}`} peladaId={peladaId} listId={collectionId} userId={user.id} username={user.username} />
}

function QuickReport({ peladaId, listId, userId, username }: { peladaId: number; listId: number; userId: number; username: string }) {
  const [pelada, setPelada] = useState<PeladaDetail | null>(null)
  const [readError, setReadError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [receiptOpen, setReceiptOpen] = useState(false)
  const [reported, setReported] = useState(false)
  const finance = useFinance(peladaId, listId, keepSelection, pelada !== null && isWritable(pelada.subscription))
  const form = useForm<{ file: File | null }>({ resolver: zodResolver(financeReportSchema), defaultValues: { file: null } })
  useEffect(() => {
    let active = true
    getPelada(peladaId).then(data => { if (active) { setPelada(data); setReadError(null) } })
      .catch(error => { if (active) setReadError(getErrorMessage(error, "Não foi possível carregar a pelada")) })
    return () => { active = false }
  }, [peladaId, attempt])
  const retry = () => { setReadError(null); setPelada(null); setAttempt(v => v + 1); finance.refresh() }
  const error = readError ?? finance.error ?? finance.detailError
  const detail = (finance.ready || reported) && !error ? finance.detail : null
  const charge = detail?.charges.find(c => c.userId === userId && !c.canceled)
  const pending = charge && pendingFinanceNotice(charge)
  const paid = charge && (charge.paymentStatus === "PAID" || (charge.outstanding === "0.00" || charge.outstanding.startsWith("-")))
  const writable = pelada !== null && isWritable(pelada.subscription)
  const canReport = !!detail && !!charge && !pending && !paid && detail.collection.status === "OPEN" && writable && !reported
  async function submit({ file }: { file: File | null }) {
    if (!canReport || !charge || finance.locked) return
    try {
      await finance.mutate(JSON.stringify({ action: "report", listId, charge: charge.id, amount: charge.outstanding, file: file ? { name: file.name, size: file.size, modified: file.lastModified } : null }), "", command => reportFinancePayment(peladaId, listId, charge.id, command, file))
      setReported(true)
    } catch (error) { applyServerErrors(form, error, "Não foi possível informar o pagamento. Tente novamente.") }
  }
  const busy = finance.pending || form.formState.isSubmitting
  const message = reported ? "Pagamento informado. Aguardando conferência."
    : pending ? "Aguardando conferência"
    : paid ? "Pagamento já confirmado"
    : !charge ? "Não há cobrança ativa para sua conta."
    : detail?.collection.status !== "OPEN" ? "Esta cobrança não está aberta para informar pagamentos."
    : !writable ? "Pelada em somente leitura. Não é possível informar o pagamento."
    : null
  return <PageContainer width="narrow" className="page-enter">
    <PageHeader breadcrumb={[{ label: "Peladas", to: "/home" }, { label: pelada?.name ?? "Pelada", truncate: true, to: `/pelada/${peladaId}` }, { label: "Informar pagamento" }]} title="Informar pagamento" />
    {error ? <Alert variant="destructive"><AlertDescription><p>{error}</p><Button variant="outline" className="mt-3" onClick={retry}>Tentar novamente</Button></AlertDescription></Alert>
      : !detail || !pelada ? <div aria-label="Carregando cobrança" role="status" className="flex flex-col gap-4"><Skeleton className="h-8 w-2/3" /><Skeleton className="h-64 w-full" /></div>
      : <Card className="shadow-none">
        <CardHeader><CardTitle className="break-words">{detail.collection.name}</CardTitle><CardDescription className="break-words">{username}</CardDescription></CardHeader>
        <CardContent className="flex flex-col gap-5">
          {charge && <div><p className="text-sm text-muted-foreground">Valor restante</p><p className="mt-1 text-3xl font-semibold tabular-nums">{formatFinanceMoney(charge.outstanding)}</p></div>}
          {message ? <Alert role="status"><AlertDescription>{message}</AlertDescription></Alert>
            : <form id="quick-report" onSubmit={form.handleSubmit(submit, () => setReceiptOpen(true))} noValidate className="flex flex-col gap-5">
              <p className="text-sm text-muted-foreground">Você vai informar o pagamento do valor restante acima. O responsável ainda precisa conferir o recebimento.</p>
              <Collapsible open={receiptOpen} onOpenChange={setReceiptOpen}>
                <CollapsibleTrigger asChild><Button type="button" variant="outline" disabled={busy}>Anexar comprovante (opcional)</Button></CollapsibleTrigger>
                <CollapsibleContent className="pt-4"><FieldGroup><Controller control={form.control} name="file" render={({ field, fieldState }) => <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="quick-receipt">Comprovante</FieldLabel><Input id="quick-receipt" type="file" accept="image/jpeg,image/png" disabled={busy} onChange={event => field.onChange(event.target.files?.[0] ?? null)} aria-invalid={fieldState.invalid} aria-describedby="quick-retention" />
                  <FieldDescription id="quick-retention">JPEG ou PNG, até 5 MB. Disponível por {detail.collection.classification === "DAILY" ? 7 : 30} dias. O responsável ainda precisa conferir.</FieldDescription><FieldError errors={[fieldState.error]} />
                </Field>} /></FieldGroup></CollapsibleContent>
              </Collapsible>
              {form.formState.errors.root?.message && <Alert variant="destructive"><AlertDescription>{form.formState.errors.root.message}</AlertDescription></Alert>}
            </form>}
        </CardContent>
        {canReport && <CardFooter><Button form="quick-report" type="submit" variant="brand" size="lg" className="w-full" disabled={busy || finance.locked}>{busy ? "Enviando…" : "Paguei — avisar responsável"}</Button></CardFooter>}
      </Card>}
    <Button asChild variant="link" className="self-start"><Link to={`/pelada/${peladaId}/financeiro/${listId}`}>Ver cobrança completa</Link></Button>
  </PageContainer>
}
