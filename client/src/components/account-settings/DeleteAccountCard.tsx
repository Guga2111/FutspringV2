import { useState } from "react"
import { Link } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { Delete02Icon } from "@hugeicons/core-free-icons"
import { SettingsCard } from "@/components/SettingsCard"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { DeleteAccountDialog } from "@/components/account-settings/DeleteAccountDialog"
import { useDeletionBlockers } from "@/components/account-settings/hooks/useDeletionBlockers"
import { areaPath } from "@/utils/peladaAreas"
import type { DeletionBlocker, DeletionBlockerType } from "@/types/user"

const BLOCKER_VIEW: Record<DeletionBlockerType, { before: string; after: string; action: string; to: (id: number) => string }> = {
  CREATOR: {
    before: "Você criou ",
    after: ": transfira ou exclua a pelada.",
    action: "Abrir configurações",
    to: (id) => `${areaPath(id, "settings")}#transferir`,
  },
  SOLE_FINANCE_MANAGER: {
    before: "Você é o único responsável financeiro de ",
    after: ": designe outro na aba Configurações do Financeiro.",
    action: "Abrir Financeiro",
    to: (id) => areaPath(id, "finance"),
  },
}

// Blocked while the user created a pelada or is its only responsável financeiro: each pending item links to
// where it is solved. The server re-checks everything.
export function DeleteAccountCard() {
  const { blockers, loading, error, reload } = useDeletionBlockers()
  const [open, setOpen] = useState(false)
  const blocked = blockers === null || blockers.length > 0

  return (
    <SettingsCard
      id="excluir-conta"
      title="Excluir conta"
      description="Seus dados pessoais são apagados e você sai de todas as peladas. Nas sessões passadas você aparece como Ex-jogador. Não dá para desfazer."
      destructive
    >
      {loading ? (
        <Skeleton className="h-10 w-full rounded-md" />
      ) : error ? (
        <Alert variant="destructive">
          <AlertDescription className="flex flex-col items-start gap-2">
            Não foi possível verificar as pendências da conta.
            <Button variant="outline" size="sm" onClick={reload}>
              Tentar de novo
            </Button>
          </AlertDescription>
        </Alert>
      ) : (
        blockers && blockers.length > 0 && <BlockerList blockers={blockers} />
      )}
      <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <p className="text-sm text-muted-foreground">
          {blockers && blockers.length > 0 ? "Resolva as pendências acima para excluir a conta." : "A sua senha é pedida para confirmar."}
        </p>
        <Button variant="destructive" disabled={loading || blocked} onClick={() => setOpen(true)}>
          <HugeiconsIcon icon={Delete02Icon} data-icon="inline-start" />
          Excluir conta
        </Button>
      </div>
      {open && <DeleteAccountDialog onClose={() => setOpen(false)} onBlocked={reload} />}
    </SettingsCard>
  )
}

function BlockerList({ blockers }: { blockers: DeletionBlocker[] }) {
  return (
    <ul className="flex flex-col divide-y rounded-tile border" aria-label="Pendências">
      {blockers.map((blocker) => {
        const view = BLOCKER_VIEW[blocker.type]
        return (
          <li
            key={`${blocker.type}-${blocker.peladaId}`}
            className="flex flex-col gap-2 p-3 md:flex-row md:items-center md:justify-between"
          >
            <p className="text-sm">
              {view.before}
              <strong className="font-semibold">{blocker.peladaName}</strong>
              {view.after}
            </p>
            <Button variant="outline" size="sm" asChild className="self-start md:self-auto">
              <Link to={view.to(blocker.peladaId)}>{view.action}</Link>
            </Button>
          </li>
        )
      })}
    </ul>
  )
}
