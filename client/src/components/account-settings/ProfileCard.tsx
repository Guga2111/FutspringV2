import { Controller } from "react-hook-form"
import { HugeiconsIcon } from "@hugeicons/react"
import { Camera01Icon } from "@hugeicons/core-free-icons"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { SettingsCard, SettingsCardFooter, SettingsRootError } from "@/components/SettingsCard"
import { Button } from "@/components/ui/button"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useProfileSettings } from "@/components/account-settings/hooks/useProfileSettings"
import { IMAGE_ACCEPT } from "@/schemas/upload"
import { EDITABLE_POSITIONS, positionLabel } from "@/types/user"
import type { UserResponseDTO } from "@/types/auth"

// Select can't hold an empty value, so "no position" uses a sentinel
const NO_POSITION = "__none__"

export function ProfileCard({ user }: { user: UserResponseDTO }) {
  const { form, onSubmit, uploading, uploadPhoto } = useProfileSettings(user)
  const { isDirty, isSubmitting } = form.formState

  return (
    <SettingsCard
      id="perfil"
      title="Perfil"
      description="Como os outros jogadores veem você. As estrelas são definidas pelos admins de cada pelada."
      onSubmit={onSubmit}
      footer={<SettingsCardFooter dirty={isDirty} submitting={isSubmitting} onCancel={() => form.reset()} />}
    >
      <div className="flex items-center gap-4">
        <PlayerAvatar userId={user.id} username={user.username} image={user.image} className="size-16 text-lg" />
        <div className="flex flex-col gap-1.5">
          <Button type="button" variant="outline" size="sm" asChild disabled={uploading}>
            <label htmlFor="profile-photo" className="cursor-pointer" aria-disabled={uploading}>
              <HugeiconsIcon icon={Camera01Icon} data-icon="inline-start" className="size-4" />
              {uploading ? "Enviando..." : user.image ? "Trocar foto" : "Enviar foto"}
            </label>
          </Button>
          <p className="text-caption text-muted-foreground">JPEG, PNG ou WebP, até 5 MB.</p>
        </div>
        <Input
          id="profile-photo"
          type="file"
          accept={IMAGE_ACCEPT}
          className="hidden"
          disabled={uploading}
          onChange={(event) => {
            void uploadPhoto(event.target.files?.[0])
            event.target.value = ""
          }}
        />
      </div>
      <FieldGroup className="gap-5 md:grid md:grid-cols-2">
        <Controller
          name="username"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="profile-username">Nome de usuário</FieldLabel>
              <Input {...field} id="profile-username" autoComplete="username" aria-invalid={fieldState.invalid} />
              {fieldState.error ? (
                <FieldError errors={[fieldState.error]} />
              ) : (
                <FieldDescription>De 3 a 30 caracteres.</FieldDescription>
              )}
            </Field>
          )}
        />
        <Controller
          name="position"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="profile-position">Posição</FieldLabel>
              <Select
                value={field.value === "" ? NO_POSITION : field.value}
                onValueChange={(value) => field.onChange(value === NO_POSITION ? "" : value)}
              >
                <SelectTrigger id="profile-position" aria-invalid={fieldState.invalid}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value={NO_POSITION}>Sem posição</SelectItem>
                    {EDITABLE_POSITIONS.map((position) => (
                      <SelectItem key={position} value={position}>
                        {positionLabel[position]}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />
      </FieldGroup>
      <SettingsRootError message={form.formState.errors.root?.message} />
    </SettingsCard>
  )
}
