import { AuthTextField } from "@/components/auth/AuthTextField"
import { SettingsCard, SettingsCardFooter, SettingsRootError } from "@/components/SettingsCard"
import { FieldGroup } from "@/components/ui/field"
import { useChangePassword } from "@/components/account-settings/hooks/useChangePassword"

export function SecurityCard() {
  const { form, onSubmit } = useChangePassword()
  const { isDirty, isSubmitting } = form.formState

  return (
    <SettingsCard
      id="seguranca"
      title="Senha"
      description="Ao trocar a senha, os outros dispositivos conectados à sua conta são desconectados."
      onSubmit={onSubmit}
      footer={<SettingsCardFooter dirty={isDirty} submitting={isSubmitting} onCancel={() => form.reset()} />}
    >
      <FieldGroup className="gap-5">
        <AuthTextField
          control={form.control}
          name="currentPassword"
          id="current-password"
          label="Senha atual"
          type="password"
          autoComplete="current-password"
        />
        <div className="flex flex-col gap-5 md:grid md:grid-cols-2">
          <AuthTextField
            control={form.control}
            name="newPassword"
            id="new-password"
            label="Nova senha"
            type="password"
            autoComplete="new-password"
            description="Pelo menos 8 caracteres."
          />
          <AuthTextField
            control={form.control}
            name="confirmPassword"
            id="confirm-password"
            label="Confirme a nova senha"
            type="password"
            autoComplete="new-password"
          />
        </div>
      </FieldGroup>
      <SettingsRootError message={form.formState.errors.root?.message} />
    </SettingsCard>
  )
}
