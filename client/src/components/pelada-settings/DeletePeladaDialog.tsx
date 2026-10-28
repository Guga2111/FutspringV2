import { useMemo } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { deletePelada } from "@/api/peladas"
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
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { applyServerErrors } from "@/lib/form-errors"
import { makeDeletePeladaSchema, type DeletePeladaValues } from "@/schemas/pelada"
import type { PeladaDetail } from "@/types/pelada"
import { ConfirmNameField } from "@/components/pelada-settings/ConfirmNameField"
import { SettingsRootError } from "@/components/pelada-settings/SettingsCard"

interface DeletePeladaDialogProps {
  pelada: PeladaDetail
  onClose: () => void
}

// On success the pelada is gone: back to /home and the sidebar is reloaded
export function DeletePeladaDialog({ pelada, onClose }: DeletePeladaDialogProps) {
  const navigate = useNavigate()
  const { reload: reloadMyPeladas } = useMyPeladasContext()
  const schema = useMemo(() => makeDeletePeladaSchema(pelada.name), [pelada.name])
  const form = useForm<DeletePeladaValues>({
    resolver: zodResolver(schema),
    defaultValues: { confirmation: "" },
  })
  const submitting = form.formState.isSubmitting

  async function onSubmit() {
    try {
      await deletePelada(pelada.id)
      toast.success("Pelada excluída")
      reloadMyPeladas()
      navigate("/home")
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível excluir a pelada")
    }
  }

  return (
    <AlertDialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <AlertDialogContent className="max-w-md">
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5">
          <AlertDialogHeader>
            <AlertDialogTitle>Excluir pelada</AlertDialogTitle>
            <AlertDialogDescription>
              Essa ação não pode ser desfeita. Sessões, resultados, times, ranking, prêmios e mensagens de{" "}
              <strong className="text-foreground">{pelada.name}</strong> serão apagados.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <ConfirmNameField control={form.control} name="confirmation" peladaName={pelada.name} />
          <SettingsRootError message={form.formState.errors.root?.message} />
          <AlertDialogFooter className="gap-2">
            <AlertDialogCancel type="button" disabled={submitting}>
              Cancelar
            </AlertDialogCancel>
            <Button type="submit" variant="destructive" disabled={submitting}>
              {submitting ? "Excluindo..." : "Excluir pelada"}
            </Button>
          </AlertDialogFooter>
        </form>
      </AlertDialogContent>
    </AlertDialog>
  )
}
