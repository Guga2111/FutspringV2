import { Controller, type Control, type FieldValues, type Path } from "react-hook-form"
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"

interface ConfirmNameFieldProps<T extends FieldValues> {
  control: Control<T>
  name: Path<T>
  peladaName: string
}

// "Digite o nome da pelada": the safety check of the danger-zone dialogs
export function ConfirmNameField<T extends FieldValues>({ control, name, peladaName }: ConfirmNameFieldProps<T>) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor="settings-confirm-name">
            Digite <strong className="font-semibold">{peladaName}</strong> para confirmar
          </FieldLabel>
          <Input {...field} id="settings-confirm-name" autoComplete="off" spellCheck={false} aria-invalid={fieldState.invalid} />
          <FieldDescription>Maiúsculas e minúsculas contam.</FieldDescription>
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )
}
