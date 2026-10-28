import { useMemo } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { transferPelada } from "@/api/peladas"
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
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { applyServerErrors } from "@/lib/form-errors"
import { makeTransferPeladaSchema, type TransferPeladaValues } from "@/schemas/pelada"
import type { PeladaDetail, PeladaMember } from "@/types/pelada"
import { ConfirmNameField } from "@/components/pelada-settings/ConfirmNameField"
import { detailFields } from "@/components/pelada-settings/hooks/usePeladaSettings"
import { SettingsRootError } from "@/components/pelada-settings/SettingsCard"

interface TransferPeladaDialogProps {
  pelada: PeladaDetail
  // the admins who can receive the pelada (the creator excluded)
  admins: PeladaMember[]
  onClose: () => void
  onTransferred: (patch: Partial<PeladaDetail>) => void
}

export function TransferPeladaDialog({ pelada, admins, onClose, onTransferred }: TransferPeladaDialogProps) {
  const schema = useMemo(() => makeTransferPeladaSchema(pelada.name), [pelada.name])
  const form = useForm<TransferPeladaValues>({
    resolver: zodResolver(schema),
    defaultValues: { userId: "", confirmation: "" },
  })
  const submitting = form.formState.isSubmitting

  async function onSubmit(values: TransferPeladaValues) {
    const userId = Number(values.userId)
    try {
      const saved = await transferPelada(pelada.id, userId)
      const target = admins.find((a) => a.id === userId)
      onTransferred({ ...detailFields(saved), creatorId: userId })
      toast.success(`Pelada transferida para ${target?.username ?? "o novo criador"}`)
      onClose()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível transferir a pelada")
    }
  }

  return (
    <AlertDialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <AlertDialogContent className="max-w-md">
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5">
          <AlertDialogHeader>
            <AlertDialogTitle>Transferir pelada</AlertDialogTitle>
            <AlertDialogDescription>
              O novo criador passa a ser o único que pode excluir ou transferir a pelada. Você continua admin.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <FieldGroup className="gap-4">
            <Controller
              name="userId"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="settings-transfer-to">Novo criador</FieldLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="settings-transfer-to" aria-invalid={fieldState.invalid}>
                      <SelectValue placeholder="Escolha um admin" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {admins.map((admin) => (
                          <SelectItem key={admin.id} value={String(admin.id)}>
                            {admin.username}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <ConfirmNameField control={form.control} name="confirmation" peladaName={pelada.name} />
          </FieldGroup>
          <SettingsRootError message={form.formState.errors.root?.message} />
          <AlertDialogFooter className="gap-2">
            <AlertDialogCancel type="button" disabled={submitting}>
              Cancelar
            </AlertDialogCancel>
            <Button type="submit" variant="destructive" disabled={submitting}>
              {submitting ? "Transferindo..." : "Transferir pelada"}
            </Button>
          </AlertDialogFooter>
        </form>
      </AlertDialogContent>
    </AlertDialog>
  )
}
