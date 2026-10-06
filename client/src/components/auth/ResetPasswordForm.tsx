import { Controller } from "react-hook-form"
import { Link } from "react-router-dom"
import { useResetPasswordForm } from "@/components/auth/hooks/useResetPasswordForm"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { PASSWORD_MIN_LENGTH } from "@/schemas/auth"

// The API error for a bad link (400 "Link inválido ou expirado…") lands on root, next to the way out
export function ResetPasswordForm({ token }: { token: string }) {
  const { form, onSubmit } = useResetPasswordForm(token)
  const { isSubmitting, errors } = form.formState

  return (
    <Card className="w-full max-w-md border-0 shadow-none">
      <CardHeader className="text-center">
        <CardTitle>Crie uma nova senha</CardTitle>
        <CardDescription>Depois de salvar, você entra de novo com a nova senha em todos os dispositivos.</CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit} noValidate>
        <CardContent className="flex flex-col gap-4">
          <FieldGroup>
            <Controller
              name="newPassword"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="new-password">Nova senha</FieldLabel>
                  <Input
                    {...field}
                    id="new-password"
                    type="password"
                    autoComplete="new-password"
                    placeholder="••••••••"
                    autoFocus
                    aria-invalid={fieldState.invalid}
                  />
                  {fieldState.error ? (
                    <FieldError errors={[fieldState.error]} />
                  ) : (
                    <FieldDescription>Pelo menos {PASSWORD_MIN_LENGTH} caracteres.</FieldDescription>
                  )}
                </Field>
              )}
            />
            <Controller
              name="confirmPassword"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="confirm-password">Confirme a nova senha</FieldLabel>
                  <Input
                    {...field}
                    id="confirm-password"
                    type="password"
                    autoComplete="new-password"
                    placeholder="••••••••"
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
          </FieldGroup>
          {errors.root && (
            <Alert variant="destructive">
              <AlertDescription>
                {errors.root.message}{" "}
                <Link to="/forgot-password" className="font-medium underline underline-offset-4">
                  Pedir um novo link
                </Link>
              </AlertDescription>
            </Alert>
          )}
        </CardContent>
        <CardFooter>
          <Button type="submit" variant="gradient" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "Salvando..." : "Salvar nova senha"}
          </Button>
        </CardFooter>
      </form>
    </Card>
  )
}
