import type { FieldValues, UseFormReturn } from "react-hook-form"
import { toast } from "sonner"
import { applyServerErrors } from "@/lib/form-errors"

interface Messages {
  success: string
  fallback: string
}

// Submit of a settings card: saves, marks the card clean with the saved values (the footer goes back to
// "Tudo salvo.") and toasts; API errors go to the card's fields or its root alert
export function useSettingsSubmit<T extends FieldValues>(
  form: UseFormReturn<T>,
  save: (values: T) => Promise<void>,
  messages: Messages,
) {
  return form.handleSubmit(async (values) => {
    try {
      await save(values)
      form.reset(values)
      toast.success(messages.success)
    } catch (error) {
      applyServerErrors(form, error, messages.fallback)
    }
  })
}
