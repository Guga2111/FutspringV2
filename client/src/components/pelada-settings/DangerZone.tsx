import { useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowDataTransferHorizontalIcon, Delete02Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import type { PeladaDetail } from "@/types/pelada"
import { DeletePeladaDialog } from "@/components/pelada-settings/DeletePeladaDialog"
import { SettingsCard } from "@/components/pelada-settings/SettingsCard"
import { TransferPeladaDialog } from "@/components/pelada-settings/TransferPeladaDialog"

interface DangerZoneProps {
  pelada: PeladaDetail
  isCreator: boolean
  onTransferred: (patch: Partial<PeladaDetail>) => void
}

// Creator-only actions; other admins see them disabled so they know who to ask
export function DangerZone({ pelada, isCreator, onTransferred }: DangerZoneProps) {
  const [open, setOpen] = useState<"transfer" | "delete" | null>(null)
  const admins = pelada.members.filter((m) => m.isAdmin && m.id !== pelada.creatorId)

  const transferHint = !isCreator
    ? "Apenas o criador pode transferir a pelada."
    : admins.length === 0
      ? "Torne alguém admin antes de transferir."
      : null

  return (
    <>
      <SettingsCard
        id="transferir"
        title="Transferir pelada"
        description="Passe a pelada para outro admin. Você continua admin, mas deixa de poder excluí-la ou transferi-la."
        destructive
      >
        <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <p className="text-sm text-muted-foreground">{transferHint ?? `${admins.length} ${admins.length === 1 ? "admin pode" : "admins podem"} receber a pelada.`}</p>
          <Button variant="outline" disabled={transferHint !== null} onClick={() => setOpen("transfer")}>
            <HugeiconsIcon icon={ArrowDataTransferHorizontalIcon} data-icon="inline-start" />
            Transferir pelada
          </Button>
        </div>
      </SettingsCard>

      <SettingsCard
        id="excluir"
        title="Excluir pelada"
        description="Apaga sessões, resultados, times, ranking, prêmios e mensagens. As estatísticas gerais dos jogadores são recalculadas sem esta pelada."
        destructive
      >
        <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <p className="text-sm text-muted-foreground">
            {isCreator ? "Essa ação não pode ser desfeita." : "Apenas o criador pode excluir a pelada."}
          </p>
          <Button variant="destructive" disabled={!isCreator} onClick={() => setOpen("delete")}>
            <HugeiconsIcon icon={Delete02Icon} data-icon="inline-start" />
            Excluir pelada
          </Button>
        </div>
      </SettingsCard>

      {open === "transfer" && (
        <TransferPeladaDialog pelada={pelada} admins={admins} onClose={() => setOpen(null)} onTransferred={onTransferred} />
      )}
      {open === "delete" && <DeletePeladaDialog pelada={pelada} onClose={() => setOpen(null)} />}
    </>
  )
}
