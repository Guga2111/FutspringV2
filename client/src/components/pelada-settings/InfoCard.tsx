import { useRef, useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { HugeiconsIcon } from "@hugeicons/react"
import { Delete02Icon, ImageUpload01Icon } from "@hugeicons/core-free-icons"
import { toast } from "sonner"
import { removePeladaImage, updatePelada, uploadPeladaImage } from "@/api/peladas"
import { ConfirmActionDialog } from "@/components/ConfirmActionDialog"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { getErrorMessage } from "@/lib/errors"
import { peladaInfoSchema, type PeladaInfoValues } from "@/schemas/pelada"
import { IMAGE_ACCEPT, validateImageFile } from "@/schemas/upload"
import type { PeladaDetail } from "@/types/pelada"
import { detailFields } from "@/components/pelada-settings/hooks/usePeladaSettings"
import { useSettingsSubmit } from "@/components/pelada-settings/hooks/useSettingsSubmit"
import { SettingsCard, SettingsCardFooter, SettingsRootError } from "@/components/pelada-settings/SettingsCard"

interface InfoCardProps {
  pelada: PeladaDetail
  onSaved: (patch: Partial<PeladaDetail>) => void
}

// Photo (saved as soon as it is picked or removed) and name (saved with the footer)
export function InfoCard({ pelada, onSaved }: InfoCardProps) {
  const form = useForm<PeladaInfoValues>({
    resolver: zodResolver(peladaInfoSchema),
    defaultValues: { name: pelada.name },
  })
  const submit = useSettingsSubmit(
    form,
    async (values) => onSaved(detailFields(await updatePelada(pelada.id, values))),
    { success: "Nome atualizado", fallback: "Não foi possível salvar o nome" },
  )

  const fileInput = useRef<HTMLInputElement>(null)
  const [imagePending, setImagePending] = useState<"upload" | "remove" | null>(null)
  const [imageError, setImageError] = useState<string | null>(null)
  const [confirmRemove, setConfirmRemove] = useState(false)

  async function pickImage(file: File | undefined) {
    if (!file) return
    const error = validateImageFile(file)
    setImageError(error)
    if (error) return
    setImagePending("upload")
    try {
      onSaved(detailFields(await uploadPeladaImage(pelada.id, file)))
      toast.success("Imagem atualizada")
    } catch (err) {
      toast.error(getErrorMessage(err, "Não foi possível enviar a imagem"))
    } finally {
      setImagePending(null)
    }
  }

  async function removeImage() {
    setImagePending("remove")
    try {
      onSaved(detailFields(await removePeladaImage(pelada.id)))
      toast.success("Imagem removida")
    } catch (err) {
      toast.error(getErrorMessage(err, "Não foi possível remover a imagem"))
    } finally {
      setImagePending(null)
      setConfirmRemove(false)
    }
  }

  return (
    <SettingsCard
      id="informacoes"
      title="Informações"
      description="O nome e a foto aparecem no menu lateral e no topo da página da pelada."
      onSubmit={submit}
      footer={
        <SettingsCardFooter
          dirty={form.formState.isDirty}
          submitting={form.formState.isSubmitting}
          onCancel={() => form.reset()}
        />
      }
    >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <PeladaAvatar name={pelada.name} image={pelada.image} className="size-20 rounded-tile text-2xl" />
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={imagePending !== null}
              onClick={() => fileInput.current?.click()}
            >
              <HugeiconsIcon icon={ImageUpload01Icon} data-icon="inline-start" />
              {imagePending === "upload" ? "Enviando..." : "Trocar imagem"}
            </Button>
            {pelada.image && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="text-destructive hover:text-destructive"
                disabled={imagePending !== null}
                onClick={() => setConfirmRemove(true)}
              >
                <HugeiconsIcon icon={Delete02Icon} data-icon="inline-start" />
                Remover
              </Button>
            )}
          </div>
          <Field data-invalid={imageError !== null}>
            <Input
              ref={fileInput}
              id="settings-image"
              type="file"
              accept={IMAGE_ACCEPT}
              className="sr-only"
              tabIndex={-1}
              aria-label="Imagem da pelada"
              onChange={(e) => {
                void pickImage(e.target.files?.[0])
                e.target.value = ""
              }}
            />
            <p className="text-xs text-muted-foreground">JPEG, PNG ou WebP · máx. 5 MB</p>
            {imageError && <FieldError>{imageError}</FieldError>}
          </Field>
        </div>
      </div>

      <Controller
        name="name"
        control={form.control}
        render={({ field, fieldState }) => (
          <Field data-invalid={fieldState.invalid}>
            <FieldLabel htmlFor="settings-name">Nome</FieldLabel>
            <Input {...field} id="settings-name" autoComplete="off" aria-invalid={fieldState.invalid} />
            <FieldError errors={[fieldState.error]} />
          </Field>
        )}
      />
      <SettingsRootError message={form.formState.errors.root?.message} />

      {confirmRemove && (
        <ConfirmActionDialog
          title="Remover imagem"
          description={<p>A pelada volta a usar as iniciais no lugar da foto.</p>}
          confirmLabel="Remover imagem"
          pendingLabel="Removendo..."
          loading={imagePending === "remove"}
          onConfirm={removeImage}
          onClose={() => setConfirmRemove(false)}
        />
      )}
    </SettingsCard>
  )
}
