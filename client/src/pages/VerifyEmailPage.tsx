import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Link, useSearchParams } from "react-router-dom"
import { MailValidation01Icon } from "@hugeicons/core-free-icons"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { AuthHeader } from "@/components/auth/AuthHeader"
import { AuthTextField } from "@/components/auth/AuthTextField"
import { Button } from "@/components/ui/button"
import { FieldGroup } from "@/components/ui/field"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { verifyEmail } from "@/api/auth"
import { verifyEmailSchema, type VerifyEmailValues } from "@/schemas/auth"
import { applyServerErrors } from "@/lib/form-errors"

export default function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get("token")
  const [verified, setVerified] = useState(false)
  const form = useForm<VerifyEmailValues>({ resolver: zodResolver(verifyEmailSchema), defaultValues: { password: "" } })
  const { errors, isSubmitting } = form.formState
  const submit = form.handleSubmit(async ({ password }) => {
    if (!token) return
    try { await verifyEmail({ token, password }); setVerified(true); form.reset() }
    catch (error) { applyServerErrors(form, error, "Não foi possível verificar seu e-mail.") }
  })
  return <AuthLayout><div className="flex w-full max-w-sm flex-col gap-6">
    <AuthHeader icon={MailValidation01Icon} title={verified ? "E-mail verificado" : "Confirme seu e-mail"}>
      {verified ? "Agora você pode entrar na sua conta." : token ? "Informe a senha da sua conta para confirmar este endereço." : "O link está incompleto. Peça um novo e-mail de verificação."}
    </AuthHeader>
    {token && !verified && <form onSubmit={submit} noValidate className="flex flex-col gap-5">
      <FieldGroup><AuthTextField control={form.control} name="password" id="verify-password" label="Senha da conta" type="password" autoComplete="current-password" /></FieldGroup>
      {errors.root && <Alert variant="destructive"><AlertDescription>{errors.root.message}</AlertDescription></Alert>}
      <Button type="submit" variant="brand" size="lg" disabled={isSubmitting}>{isSubmitting ? "Verificando…" : "Verificar e-mail"}</Button>
      <Button asChild variant="ghost"><Link to="/forgot-password">Esqueci minha senha</Link></Button>
    </form>}
    <Button asChild variant={verified ? "brand" : "outline"}><Link to={verified ? "/auth?tab=login" : "/verification-email"}>{verified ? "Entrar" : "Pedir um novo link"}</Link></Button>
  </div></AuthLayout>
}
