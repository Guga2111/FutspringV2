import type { FieldValues, Path, UseFormReturn } from "react-hook-form"
import { getApiErrorBody, getErrorMessage } from "@/lib/errors"

// Puts the API's field errors on the matching form fields; anything else goes to the form's root error
export function applyServerErrors<T extends FieldValues, TOutput = T>(
  form: UseFormReturn<T, unknown, TOutput>,
  error: unknown,
  fallback: string,
) {
  const fieldErrors = getApiErrorBody(error)?.errors ?? {}
  const formFields = Object.keys(form.getValues())
  let applied = false
  for (const [field, message] of Object.entries(fieldErrors)) {
    if (formFields.includes(field)) {
      form.setError(field as Path<T>, { type: "server", message })
      applied = true
    }
  }
  if (!applied) {
    form.setError("root", { type: "server", message: getErrorMessage(error, fallback) })
  }
}
