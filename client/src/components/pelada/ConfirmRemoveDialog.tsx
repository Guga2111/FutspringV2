import { ConfirmActionDialog } from "@/components/ConfirmActionDialog"
import type { PeladaMember } from "@/types/pelada"

export function ConfirmRemoveDialog({
  member,
  onConfirm,
  onClose,
  loading,
}: {
  member: PeladaMember
  onConfirm: () => void
  onClose: () => void
  loading: boolean
}) {
  return (
    <ConfirmActionDialog
      title="Remover jogador"
      description={
        <p>
          Remover <strong className="text-foreground">{member.username}</strong> desta pelada?
        </p>
      }
      confirmLabel="Remover"
      pendingLabel="Removendo..."
      loading={loading}
      onConfirm={onConfirm}
      onClose={onClose}
    />
  )
}
