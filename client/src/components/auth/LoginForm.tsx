import { Link } from "react-router-dom"
import { AuthTextField } from "@/components/auth/AuthTextField"
import { useLoginForm } from "@/components/auth/hooks/useAuthForms"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"

export function LoginForm({ returnTo }: { returnTo: string }) {
  const { form, onSubmit } = useLoginForm(returnTo)
  const { isSubmitting, errors } = form.formState

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
      <FieldGroup className="gap-4">
        <AuthTextField control={form.control} name="email" id="login-email" label="E-mail" type="email" autoComplete="email" placeholder="voce@email.com" />
        <AuthTextField
          control={form.control}
          name="password"
          id="login-password"
          label="Senha"
          type="password"
          autoComplete="current-password"
          placeholder="Sua senha"
          labelAction={
            <Link to="/forgot-password" className="text-body-sm text-brand-text underline-offset-4 hover:underline">
              Esqueceu a senha?
            </Link>
          }
        />
      </FieldGroup>
      {errors.root && (
        <Alert variant="destructive">
          <AlertDescription>{errors.root.message}</AlertDescription>
        </Alert>
      )}
      <Button type="submit" variant="brand" size="lg" className="w-full" disabled={isSubmitting}>
        {isSubmitting ? "Entrando…" : "Entrar"}
      </Button>
    </form>
  )
}
