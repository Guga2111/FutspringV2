import { ConfirmActionDialog } from "@/components/ConfirmActionDialog"

interface StatusConfirmDialogProps {
  title: string
  description: string
  // pending items to read before confirming (short teams, players without a team)
  details?: string[]
  confirmLabel?: string
  loading: boolean
  variant?: "destructive" | "default" | "brand"
  onConfirm: () => void
  onClose: () => void
}

export default function StatusConfirmDialog({
  title,
  description,
  details = [],
  confirmLabel = "Confirmar",
  loading,
  variant = "destructive",
  onConfirm,
  onClose,
}: StatusConfirmDialogProps) {
  return (
    <ConfirmActionDialog
      title={title}
      description={
        <>
          <p>{description}</p>
          {details.length > 0 && (
            <ul className="list-disc pl-5 text-foreground">
              {details.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          )}
        </>
      }
      confirmLabel={confirmLabel}
      pendingLabel="Atualizando..."
      cancelLabel="Voltar"
      variant={variant}
      loading={loading}
      onConfirm={onConfirm}
      onClose={onClose}
    />
  )
}
