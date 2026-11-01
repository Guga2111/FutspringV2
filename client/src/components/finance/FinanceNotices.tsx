import { useEffect, useState } from "react"
import { getFinanceNotices } from "@/api/finance"
import type { FinanceNotice, FinancePage } from "@/types/finance"
import { financeNoticeLabel } from "@/types/finance"
import { FinanceImage } from "@/components/finance/FinanceImage"
import { FinanceFormError } from "@/components/finance/FinanceFields"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { getErrorMessage } from "@/lib/errors"
import { formatFinanceMoney } from "@/utils/finance"
export function FinanceNotices({ peladaId, listId, chargeId, recent }: { peladaId: number; listId: number; chargeId: number; recent: FinanceNotice[] }) {
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<{ key: string; data: FinancePage<FinanceNotice> | null; error: string | null } | null>(null)
  const key = `${peladaId}:${listId}:${chargeId}:${page}:${attempt}`
  useEffect(() => {
    if (page === 0) return
    let cancelled = false
    getFinanceNotices(peladaId, listId, chargeId, page).then(data => { if (!cancelled) setResult({ key, data, error: null }) }).catch(e => { if (!cancelled) setResult({ key, data: null, error: getErrorMessage(e, "Não foi possível carregar os avisos") }) })
    return () => { cancelled = true }
  }, [peladaId, listId, chargeId, page, key])
  const data = page === 0 ? { items: recent, hasMore: recent.length === 10 } : result?.key === key ? result.data : null
  const error = result?.key === key ? result.error : null
  return <div className="flex flex-col gap-3 pt-3">{error ? <><FinanceFormError message={error} /><Button variant="outline" onClick={() => setAttempt(v => v + 1)}>Tentar de novo</Button></> : !data ? <Skeleton className="h-24 w-full" /> : <ul className="flex flex-col gap-3">{data.items.map(notice => <li key={notice.id} className="flex flex-col items-start gap-2 rounded-md bg-muted p-3 text-sm"><p>{financeNoticeLabel[notice.status]} · {formatFinanceMoney(notice.amount)} · {new Date(notice.createdAt).toLocaleString("pt-BR")}</p>{notice.reason && <p className="break-words">Motivo: {notice.reason}</p>}{notice.receiptExpiresAt && <p className="text-muted-foreground">Comprovante {notice.receiptAvailable ? `disponível até ${new Date(notice.receiptExpiresAt).toLocaleString("pt-BR")}` : "expirado ou indisponível"}</p>}{notice.receiptAvailable && <FinanceImage peladaId={peladaId} kind="receipt" imageId={notice.id} expiresAt={notice.receiptExpiresAt} />}</li>)}</ul>}
    {(page > 0 || data?.hasMore) && <div className="flex justify-between gap-2"><Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(v => v - 1)}>Avisos recentes</Button><Button variant="outline" size="sm" disabled={!data?.hasMore} onClick={() => setPage(v => v + 1)}>Avisos anteriores</Button></div>}
  </div>
}
