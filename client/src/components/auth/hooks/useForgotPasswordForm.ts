import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { requestPasswordReset } from "@/api/auth"
import { applyServerErrors } from "@/lib/form-errors"
import { forgotPasswordSchema, type ForgotPasswordValues } from "@/schemas/auth"

// The success screen comes from formState.isSubmitSuccessful; form.reset() goes back to the form
export function useForgotPasswordForm() {
  const form = useForm<ForgotPasswordValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
  })

  async function onSubmit(values: ForgotPasswordValues) {
    try {
      await requestPasswordReset(values.email)
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível enviar o link. Tente novamente.")
    }
  }

  return { form, onSubmit: form.handleSubmit(onSubmit) }
}
