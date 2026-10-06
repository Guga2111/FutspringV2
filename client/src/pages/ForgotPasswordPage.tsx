import { Controller } from "react-hook-form"
import { Link } from "react-router-dom"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { ResetLinkSent } from "@/components/auth/ResetLinkSent"
import { useForgotPasswordForm } from "@/components/auth/hooks/useForgotPasswordForm"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"

export default function ForgotPasswordPage() {
  const { form, onSubmit } = useForgotPasswordForm()
  const { isSubmitting, isSubmitSuccessful, errors } = form.formState

  if (isSubmitSuccessful) {
    return (
      <AuthLayout>
        <ResetLinkSent email={form.getValues("email")} onUseAnotherEmail={() => form.reset()} />
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <Card className="w-full max-w-md border-0 shadow-none">
        <CardHeader className="text-center">
          <CardTitle>Esqueceu a senha?</CardTitle>
          <CardDescription>Informe o e-mail da sua conta e enviaremos um link para criar uma nova senha.</CardDescription>
        </CardHeader>
        <form onSubmit={onSubmit} noValidate>
          <CardContent className="flex flex-col gap-4">
            <FieldGroup>
              <Controller
                name="email"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="forgot-email">E-mail</FieldLabel>
                    <Input
                      {...field}
                      id="forgot-email"
                      type="email"
                      autoComplete="email"
                      placeholder="salah@futspring.com"
                      autoFocus
                      aria-invalid={fieldState.invalid}
                    />
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
          </CardContent>
          <CardFooter className="flex flex-col gap-2">
            <Button type="submit" variant="gradient" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? "Enviando..." : "Enviar link"}
            </Button>
            <Button asChild variant="ghost" className="w-full">
              <Link to="/auth?tab=login">Voltar para o login</Link>
            </Button>
          </CardFooter>
        </form>
      </Card>
    </AuthLayout>
  )
}
