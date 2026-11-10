import { ConfirmActionDialog } from "@/components/ConfirmActionDialog"
import type { PeladaMember } from "@/types/pelada"

export function ConfirmRemoveDialog({
  member,
  onConfirm,
  onClose,
}: {
  member: PeladaMember
  // the page closes the dialog and the removal runs in the background (the card is dimmed meanwhile)
  onConfirm: () => void
  onClose: () => void
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
      loading={false}
      onConfirm={onConfirm}
      onClose={onClose}
    />
  )
}
