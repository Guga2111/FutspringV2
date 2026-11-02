import { Controller } from "react-hook-form"
import { AuthTextField } from "@/components/auth/AuthTextField"
import { useRegisterForm } from "@/components/auth/hooks/useAuthForms"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { PASSWORD_MIN_LENGTH } from "@/schemas/auth"

export function RegisterForm({ returnTo }: { returnTo: string }) {
  const { form, onSubmit } = useRegisterForm(returnTo)
  const { isSubmitting, errors } = form.formState

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
      <FieldGroup className="gap-4">
        <AuthTextField control={form.control} name="username" id="register-username" label="Nome de usuário" autoComplete="username" placeholder="Como te chamam na pelada" />
        <AuthTextField control={form.control} name="email" id="register-email" label="E-mail" type="email" autoComplete="email" placeholder="voce@email.com" />
        <AuthTextField
          control={form.control}
          name="password"
          id="register-password"
          label="Senha"
          type="password"
          autoComplete="new-password"
          description={`Pelo menos ${PASSWORD_MIN_LENGTH} caracteres.`}
        />
        <AuthTextField control={form.control} name="confirmPassword" id="register-confirm" label="Confirme a senha" type="password" autoComplete="new-password" />
        <Controller
          name="terms"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid} className="gap-2">
              <Field orientation="horizontal" className="gap-2">
                <Checkbox
                  id="register-terms"
                  checked={field.value}
                  onCheckedChange={(checked) => field.onChange(checked === true)}
                  onBlur={field.onBlur}
                  aria-invalid={fieldState.invalid}
                />
                <FieldLabel htmlFor="register-terms" className="font-normal text-muted-foreground">
                  Li e concordo com os termos de uso
                </FieldLabel>
              </Field>
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />
      </FieldGroup>
      {errors.root && (
        <Alert variant="destructive">
          <AlertDescription>{errors.root.message}</AlertDescription>
        </Alert>
      )}
      <Button type="submit" variant="brand" size="lg" className="w-full" disabled={isSubmitting}>
        {isSubmitting ? "Criando conta…" : "Criar conta"}
      </Button>
    </form>
  )
}
