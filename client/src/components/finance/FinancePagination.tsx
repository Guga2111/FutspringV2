import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowLeft01Icon, ArrowRight01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { financePageCount } from "@/utils/finance"

interface FinancePaginationProps {
  page: number
  total: number
  size: number
  // singular and plural of what is paged: ["cobrança", "cobranças"]
  noun: [string, string]
  disabled?: boolean
  onPage: (page: number) => void
}

// Numbered pages of the finance lists (cobranças, extrato, histórico), visible as soon as there is something to page
export function FinancePagination({ page, total, size, noun, disabled, onPage }: FinancePaginationProps) {
  if (total === 0) return null
  const pages = financePageCount(total, size)
  return (
    <nav aria-label={`Páginas de ${noun[1]}`} className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-muted-foreground">
        {total} {total === 1 ? noun[0] : noun[1]}
      </p>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="sm" disabled={disabled || page === 0} onClick={() => onPage(page - 1)}>
          <HugeiconsIcon icon={ArrowLeft01Icon} />
          Anterior
        </Button>
        <span className="min-w-[104px] text-center text-sm tabular-nums" aria-live="polite">
          Página {page + 1} de {pages}
        </span>
        <Button variant="outline" size="sm" disabled={disabled || page >= pages - 1} onClick={() => onPage(page + 1)}>
          Próxima
          <HugeiconsIcon icon={ArrowRight01Icon} />
        </Button>
      </div>
    </nav>
  )
}
