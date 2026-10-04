import { ConfirmActionDialog } from "@/components/ConfirmActionDialog"

interface StatusConfirmDialogProps {
  title: string
  description: string
  loading: boolean
  variant?: "destructive" | "default" | "gradient"
  onConfirm: () => void
  onClose: () => void
}

export default function StatusConfirmDialog({
  title,
  description,
  loading,
  variant = "destructive",
  onConfirm,
  onClose,
}: StatusConfirmDialogProps) {
  return (
    <ConfirmActionDialog
      title={title}
      description={<p>{description}</p>}
      confirmLabel="Confirmar"
      pendingLabel="Atualizando..."
      cancelLabel="Voltar"
      variant={variant}
      loading={loading}
      onConfirm={onConfirm}
      onClose={onClose}
    />
  )
}
