import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { deleteAccount } from "@/api/users"
import { getErrorStatus } from "@/lib/errors"
import { applyServerErrors } from "@/lib/form-errors"
import { deleteAccountSchema, type DeleteAccountValues } from "@/schemas/user"
import { useAuth } from "@/hooks/useAuth"

// The confirmation dialog of "Excluir conta". A 409 means a blocker appeared meanwhile (a pelada transferred to
// the user, say): the list is reloaded and the message stays in the dialog.
export function useDeleteAccount(onBlocked: () => void) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const form = useForm<DeleteAccountValues>({ resolver: zodResolver(deleteAccountSchema), defaultValues: { password: "" } })

  const onSubmit = form.handleSubmit(async ({ password }) => {
    try {
      await deleteAccount(password)
      logout()
      toast.success("Conta excluída")
      navigate("/", { replace: true })
    } catch (error) {
      if (getErrorStatus(error) === 409) onBlocked()
      applyServerErrors(form, error, "Não foi possível excluir a conta")
    }
  })

  return { form, onSubmit }
}
