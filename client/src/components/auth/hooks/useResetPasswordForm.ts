import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { resetPassword } from "@/api/auth"
import { useAuth } from "@/hooks/useAuth"
import { applyServerErrors } from "@/lib/form-errors"
import { resetPasswordSchema, type ResetPasswordValues } from "@/schemas/auth"

// The reset ends every session (backend tokenVersion), so a session stored in this browser is cleared too
export function useResetPasswordForm(token: string) {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const form = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  })

  async function onSubmit(values: ResetPasswordValues) {
    try {
      await resetPassword({ token, newPassword: values.newPassword })
      logout()
      toast.success("Senha redefinida. Entre com a nova senha.")
      navigate("/auth?tab=login", { replace: true })
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível redefinir a senha. Tente novamente.")
    }
  }

  return { form, onSubmit: form.handleSubmit(onSubmit) }
}
