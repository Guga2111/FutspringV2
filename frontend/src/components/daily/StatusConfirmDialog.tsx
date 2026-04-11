import { Button } from "../ui/button"

interface StatusConfirmDialogProps {
  description: string
  loading: boolean
  onConfirm: () => void
  onClose: () => void
}

export default function StatusConfirmDialog({
  description,
  loading,
  onConfirm,
  onClose,
}: StatusConfirmDialogProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-background rounded-lg shadow-lg p-6 max-w-sm w-full mx-4">
        <h3 className="text-lg font-semibold mb-2">Confirme Ação</h3>
        <p className="text-sm text-muted-foreground mb-6">{description}</p>
        <div className="flex gap-3 justify-end">
          <button
            className="text-sm px-4 py-2 rounded-full border hover:bg-muted disabled:opacity-50"
            disabled={loading}
            onClick={onClose}
          >
            Voltar
          </button>
          <Button
            className="text-sm text-white px-4 py-2 rounded-full disabled:opacity-50"
            disabled={loading}
            variant="gradient"
            onClick={onConfirm}
          >
            {loading ? 'Atualizando...' : 'Confirmar'}
          </Button>
        </div>
      </div>
    </div>
  )
}
