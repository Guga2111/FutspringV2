import type { ReactNode } from "react"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"

interface ConfirmActionDialogProps {
  title: string
  description: ReactNode
  confirmLabel: string
  pendingLabel: string
  cancelLabel?: string
  variant?: "destructive" | "default" | "gradient"
  loading: boolean
  onConfirm: () => void
  onClose: () => void
}

// Mounted only while open. The confirm button is a plain Button (not AlertDialogAction) so the dialog
// stays open, disabled, until the parent's async action finishes and unmounts it.
export function ConfirmActionDialog({
  title,
  description,
  confirmLabel,
  pendingLabel,
  cancelLabel = "Cancelar",
  variant = "destructive",
  loading,
  onConfirm,
  onClose,
}: ConfirmActionDialogProps) {
  return (
    <AlertDialog open onOpenChange={(open) => !open && !loading && onClose()}>
      <AlertDialogContent className="max-w-sm">
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div className="flex flex-col gap-2">{description}</div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter className="gap-2">
          <AlertDialogCancel disabled={loading}>{cancelLabel}</AlertDialogCancel>
          <Button variant={variant} disabled={loading} onClick={onConfirm}>
            {loading ? pendingLabel : confirmLabel}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
