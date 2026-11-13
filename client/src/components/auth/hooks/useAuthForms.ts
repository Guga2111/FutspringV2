import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useNavigate } from "react-router-dom"
import { loginUser, registerUser } from "@/api/auth"
import { useAuth } from "@/hooks/useAuth"
import { applyServerErrors } from "@/lib/form-errors"
import { LEGAL_VERSION } from "@/lib/legal"
import { loginSchema, registerSchema, type LoginValues, type RegisterValues } from "@/schemas/auth"

// Login: a wrong password (401) lands on root, shown above the button
export function useLoginForm(returnTo: string) {
  const navigate = useNavigate()
  const { login } = useAuth()
  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  })

  async function onSubmit(values: LoginValues) {
    try {
      const response = await loginUser(values)
      login(response.token, response.user)
      navigate(returnTo, { replace: true })
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível entrar. Confira sua conexão e tente novamente.")
    }
  }

  return { form, onSubmit: form.handleSubmit(onSubmit) }
}

// Registration: field errors from RegisterRequestDTO go to their fields, the rest (e-mail in use) to root
export function useRegisterForm(returnTo: string) {
  const navigate = useNavigate()
  const form = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { username: "", email: "", password: "", confirmPassword: "", terms: false },
  })

  async function onSubmit(values: RegisterValues) {
    try {
      // the checkbox is required by the schema; the server records the version the user accepted (409 if outdated)
      await registerUser({
        username: values.username,
        email: values.email,
        password: values.password,
        termsVersion: LEGAL_VERSION,
      })
      navigate(`/verification-email?returnTo=${encodeURIComponent(returnTo)}`, { replace: true, state: { email: values.email, sent: true } })
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível criar a conta. Tente novamente.")
    }
  }

  return { form, onSubmit: form.handleSubmit(onSubmit) }
}
