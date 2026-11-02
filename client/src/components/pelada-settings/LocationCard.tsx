import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { updatePelada } from "@/api/peladas"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { peladaLocationSchema, type PeladaLocationValues } from "@/schemas/pelada"
import type { PeladaDetail } from "@/types/pelada"
import { detailFields } from "@/components/pelada-settings/hooks/usePeladaSettings"
import { useSettingsSubmit } from "@/components/pelada-settings/hooks/useSettingsSubmit"
import { SettingsCard, SettingsCardFooter, SettingsRootError } from "@/components/SettingsCard"

interface LocationCardProps {
  pelada: PeladaDetail
  onSaved: (patch: Partial<PeladaDetail>) => void
}

export function LocationCard({ pelada, onSaved }: LocationCardProps) {
  const form = useForm<PeladaLocationValues>({
    resolver: zodResolver(peladaLocationSchema),
    defaultValues: { address: pelada.address ?? "", reference: pelada.reference ?? "" },
  })
  const submit = useSettingsSubmit(
    form,
    async (values) => onSaved(detailFields(await updatePelada(pelada.id, values))),
    { success: "Local atualizado", fallback: "Não foi possível salvar o local" },
  )

  return (
    <SettingsCard
      id="local"
      title="Local"
      description="Onde a pelada acontece. Aparece no topo da página da pelada."
      onSubmit={submit}
      footer={
        <SettingsCardFooter dirty={form.formState.isDirty} submitting={form.formState.isSubmitting} onCancel={() => form.reset()} />
      }
    >
      <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
        <Controller
          name="address"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="settings-address">Endereço</FieldLabel>
              <Input {...field} id="settings-address" placeholder="Av. Agamenon Magalhães" autoComplete="street-address" aria-invalid={fieldState.invalid} />
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />
        <Controller
          name="reference"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="settings-reference">Referência</FieldLabel>
              <Input {...field} id="settings-reference" placeholder="Ilha do Retiro" autoComplete="off" aria-invalid={fieldState.invalid} />
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />
      </div>
      <SettingsRootError message={form.formState.errors.root?.message} />
    </SettingsCard>
  )
}
