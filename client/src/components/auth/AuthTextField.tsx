import { useState, type ReactNode } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { ViewIcon, ViewOffSlashIcon } from "@hugeicons/core-free-icons"
import { Controller, type Control, type FieldPath, type FieldValues } from "react-hook-form"
import { Button } from "@/components/ui/button"
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

// One text input of the auth forms: Controller + Field, the error replacing the description; a password
// field gets a show/hide button
export function AuthTextField<T extends FieldValues>({
  control, name, id, label, type = "text", autoComplete, placeholder, autoFocus, description, labelAction,
}: AuthTextFieldProps<T>) {
  const [revealed, setRevealed] = useState(false)
  const isPassword = type === "password"
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
          <div className="relative">
            <Input
              {...field}
              id={id}
              type={isPassword && revealed ? "text" : type}
              autoComplete={autoComplete}
              placeholder={placeholder}
              autoFocus={autoFocus}
              aria-invalid={fieldState.invalid}
              className={isPassword ? "h-11 pr-12" : "h-11"}
            />
            {isPassword && (
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={revealed ? "Ocultar senha" : "Mostrar senha"}
                aria-pressed={revealed}
                onClick={() => setRevealed((value) => !value)}
                className="absolute right-1 top-1/2 size-9 -translate-y-1/2 rounded-full text-muted-foreground hover:text-foreground"
              >
                <HugeiconsIcon icon={revealed ? ViewOffSlashIcon : ViewIcon} className="size-5" />
              </Button>
            )}
          </div>
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
