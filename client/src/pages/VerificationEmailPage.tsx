import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useLocation, Link, useSearchParams } from "react-router-dom"
import { MailValidation01Icon } from "@hugeicons/core-free-icons"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { AuthHeader } from "@/components/auth/AuthHeader"
import { AuthTextField } from "@/components/auth/AuthTextField"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { requestVerificationEmail } from "@/api/auth"
import { forgotPasswordSchema, type ForgotPasswordValues } from "@/schemas/auth"
import { applyServerErrors } from "@/lib/form-errors"

export default function VerificationEmailPage() {
  const location = useLocation()
  const [params] = useSearchParams()
  const state = location.state as { email?: string; sent?: boolean } | null
  const [sent, setSent] = useState(state?.sent ?? false)
  const form = useForm<ForgotPasswordValues>({ resolver: zodResolver(forgotPasswordSchema), defaultValues: { email: state?.email ?? "" } })
  const { errors, isSubmitting } = form.formState
  const submit = form.handleSubmit(async ({ email }) => {
    try { await requestVerificationEmail(email); setSent(true) }
    catch (error) { applyServerErrors(form, error, "Não foi possível enviar o link. Tente novamente.") }
  })
  return <AuthLayout><div className="flex w-full max-w-sm flex-col gap-6">
    <AuthHeader icon={MailValidation01Icon} title="Verifique seu e-mail">
      Confirme seu endereço antes de entrar no Futspring. O link vale por 30 minutos.
    </AuthHeader>
    {sent && <Alert><AlertDescription role="status">Se houver uma conta aguardando verificação, enviamos um link. Confira também o spam. Você pode pedir outro após um minuto.</AlertDescription></Alert>}
    <form onSubmit={submit} noValidate className="flex flex-col gap-5">
      <FieldGroup><AuthTextField control={form.control} name="email" id="verification-email" label="E-mail" type="email" autoComplete="email" placeholder="voce@email.com" /></FieldGroup>
      {errors.root && <Alert variant="destructive"><AlertDescription>{errors.root.message}</AlertDescription></Alert>}
      <Button type="submit" variant="brand" size="lg" disabled={isSubmitting}>{isSubmitting ? "Enviando…" : sent ? "Enviar outro link" : "Enviar link"}</Button>
    </form>
    <Button asChild variant="ghost"><Link to={`/auth?tab=login&returnTo=${encodeURIComponent(params.get("returnTo") ?? "/home")}`}>Voltar para o login</Link></Button>
  </div></AuthLayout>
}
