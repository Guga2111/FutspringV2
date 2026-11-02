import type { ReactNode } from "react"
import { Controller, type Control, type FieldPath, type FieldValues } from "react-hook-form"
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"

interface AuthTextFieldProps<T extends FieldValues> {
  control: Control<T>
  name: FieldPath<T>
  id: string
  label: string
  type?: "text" | "email" | "password"
  autoComplete: string
  placeholder?: string
  autoFocus?: boolean
  // shown under the field while it has no error
  description?: string
  // beside the label (e.g. "Esqueceu a senha?")
  labelAction?: ReactNode
}

// One text input of the auth forms: Controller + Field, the error replacing the description
export function AuthTextField<T extends FieldValues>({
  control, name, id, label, type = "text", autoComplete, placeholder, autoFocus, description, labelAction,
}: AuthTextFieldProps<T>) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <div className="flex items-center justify-between gap-2">
            <FieldLabel htmlFor={id}>{label}</FieldLabel>
            {labelAction}
          </div>
          <Input
            {...field}
            id={id}
            type={type}
            autoComplete={autoComplete}
            placeholder={placeholder}
            autoFocus={autoFocus}
            aria-invalid={fieldState.invalid}
            className="h-11"
          />
          {fieldState.error ? (
            <FieldError errors={[fieldState.error]} />
          ) : (
            description && <FieldDescription>{description}</FieldDescription>
          )}
        </Field>
      )}
    />
  )
}
