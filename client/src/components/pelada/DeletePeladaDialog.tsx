import { ConfirmActionDialog } from "@/components/ConfirmActionDialog"

export function DeletePeladaDialog({
  peladaName,
  deleting,
  onConfirm,
  onClose,
}: {
  peladaName: string
  deleting: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <ConfirmActionDialog
      title="Excluir pelada"
      description={
        <>
          <p>
            Tem certeza que quer excluir <strong className="text-foreground">{peladaName}</strong>?
          </p>
          <p className="text-destructive">
            Essa ação não pode ser desfeita. Sessões, resultados, ranking e mensagens serão apagados.
          </p>
        </>
      }
      confirmLabel="Excluir pelada"
      pendingLabel="Excluindo..."
      loading={deleting}
      onConfirm={onConfirm}
      onClose={onClose}
    />
  )
}
