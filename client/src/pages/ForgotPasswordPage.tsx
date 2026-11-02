import { SquareLock02Icon } from "@hugeicons/core-free-icons"
import { AuthHeader } from "@/components/auth/AuthHeader"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { AuthTextField } from "@/components/auth/AuthTextField"
import { BackToLogin } from "@/components/auth/BackToLogin"
import { ResetLinkSent } from "@/components/auth/ResetLinkSent"
import { useForgotPasswordForm } from "@/components/auth/hooks/useForgotPasswordForm"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"

export default function ForgotPasswordPage() {
  const { form, onSubmit } = useForgotPasswordForm()
  const { isSubmitting, isSubmitSuccessful, errors } = form.formState

  return (
    <AuthLayout>
      {isSubmitSuccessful ? (
        <ResetLinkSent email={form.getValues("email")} onUseAnotherEmail={() => form.reset()} />
      ) : (
        <div className="flex w-full max-w-sm flex-col gap-8">
          <AuthHeader icon={SquareLock02Icon} title="Recupere sua senha">
            Informe o <span className="whitespace-nowrap">e-mail</span> da sua conta e enviaremos um link para criar
            uma nova senha. Por segurança, não dizemos se a conta existe.
          </AuthHeader>
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
            <FieldGroup>
              <AuthTextField
                control={form.control}
                name="email"
                id="forgot-email"
                label="E-mail"
                type="email"
                autoComplete="email"
                placeholder="voce@email.com"
                autoFocus
              />
            </FieldGroup>
            {errors.root && (
              <Alert variant="destructive">
                <AlertDescription>{errors.root.message}</AlertDescription>
              </Alert>
            )}
            <Button type="submit" variant="brand" size="lg" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? "Enviando…" : "Enviar link"}
            </Button>
          </form>
          <BackToLogin />
        </div>
      )}
    </AuthLayout>
  )
}
