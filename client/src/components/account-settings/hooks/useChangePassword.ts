import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { changePassword } from "@/api/users"
import { applyServerErrors } from "@/lib/form-errors"
import { changePasswordSchema, type ChangePasswordValues } from "@/schemas/user"
import { useAuth } from "@/hooks/useAuth"

const EMPTY: ChangePasswordValues = { currentPassword: "", newPassword: "", confirmPassword: "" }

// The Segurança card: the API answers a token of the new version, so this device stays logged in
export function useChangePassword() {
  const { login } = useAuth()
  const form = useForm<ChangePasswordValues>({ resolver: zodResolver(changePasswordSchema), defaultValues: EMPTY })

  const onSubmit = form.handleSubmit(async ({ currentPassword, newPassword }) => {
    try {
      const session = await changePassword({ currentPassword, newPassword })
      login(session.token, session.user)
      form.reset(EMPTY)
      toast.success("Senha alterada. Outros dispositivos foram desconectados.")
    } catch (error) {
      // a wrong current password lands on its field
      applyServerErrors(form, error, "Não foi possível alterar a senha")
    }
  })

  return { form, onSubmit }
}
