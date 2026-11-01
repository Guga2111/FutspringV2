import { useState } from "react"
import { format } from "date-fns"
import { ptBR } from "date-fns/locale"
import { Calendar } from "@/components/ui/calendar"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Controller, type Control, type FieldPath, type FieldValues } from "react-hook-form"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Checkbox } from "@/components/ui/checkbox"
import { Alert, AlertDescription } from "@/components/ui/alert"

interface Props<T extends FieldValues> { control: Control<T>; name: FieldPath<T>; label: string; type?: string; disabled?: boolean; multiline?: boolean; placeholder?: string }
export function FinanceInput<T extends FieldValues>({ control, name, label, type = "text", disabled, multiline, placeholder }: Props<T>) {
  return <Controller control={control} name={name} render={({ field, fieldState }) => <Field data-invalid={fieldState.invalid}>
    <FieldLabel htmlFor={`finance-${name}`}>{label}</FieldLabel>
    {type === "date" ? <FinanceDate id={`finance-${name}`} value={typeof field.value === "string" ? field.value : ""} onChange={field.onChange} invalid={fieldState.invalid} disabled={disabled} /> : multiline ? <Textarea {...field} id={`finance-${name}`} disabled={disabled} aria-invalid={fieldState.invalid} />
      : <Input {...field} value={typeof field.value === "string" && (name.includes("amount") || name === "adjustment") ? field.value.replace(/^(-?\d+)\.(\d{2})$/, "$1,$2") : field.value} placeholder={placeholder} type={type} inputMode={name.includes("amount") || name === "adjustment" ? "decimal" : undefined} id={`finance-${name}`} disabled={disabled} aria-invalid={fieldState.invalid} />}
    <FieldError errors={[fieldState.error]} />
  </Field>} />
}
export function FinanceSelect<T extends FieldValues>({ control, name, label, options, disabled }: Props<T> & { options: { value: string; label: string }[] }) {
  return <Controller control={control} name={name} render={({ field, fieldState }) => <Field data-invalid={fieldState.invalid}>
    <FieldLabel htmlFor={`finance-${name}`}>{label}</FieldLabel>
    <Select value={field.value} onValueChange={field.onChange} disabled={disabled}>
      <SelectTrigger id={`finance-${name}`} aria-invalid={fieldState.invalid}><SelectValue /></SelectTrigger>
      <SelectContent><SelectGroup>{options.map(o => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}</SelectGroup></SelectContent>
    </Select><FieldError errors={[fieldState.error]} />
  </Field>} />
}
export function FinanceCheck<T extends FieldValues>({ control, name, label }: Props<T>) {
  return <Controller control={control} name={name} render={({ field, fieldState }) => <Field data-invalid={fieldState.invalid}>
    <div className="flex items-center gap-3"><Checkbox id={`finance-${name}`} checked={field.value} onCheckedChange={field.onChange} aria-invalid={fieldState.invalid} /><FieldLabel htmlFor={`finance-${name}`}>{label}</FieldLabel></div>
    <FieldError errors={[fieldState.error]} />
  </Field>} />
}
export function FinanceFormError({ message }: { message?: string }) {
  return message ? <Alert variant="destructive"><AlertDescription>{message}</AlertDescription></Alert> : null
}

function FinanceDate({ id, value, onChange, invalid, disabled }: { id: string; value: string; onChange: (value: string) => void; invalid: boolean; disabled?: boolean }) {
  const [open, setOpen] = useState(false)
  const parts = value.split("-").map(Number)
  const date = value ? new Date(parts[0], parts[1] - 1, parts[2]) : undefined
  return <div className="flex items-center gap-2"><Popover open={open} onOpenChange={setOpen}><PopoverTrigger asChild><Button id={id} type="button" variant="outline" className="min-w-0 flex-1 justify-start" disabled={disabled} aria-invalid={invalid}>{date ? format(date, "PPP", { locale: ptBR }) : "Escolha uma data"}</Button></PopoverTrigger><PopoverContent className="w-auto p-0" align="start"><Calendar mode="single" locale={ptBR} selected={date} onSelect={next => { onChange(next ? format(next, "yyyy-MM-dd") : ""); setOpen(false) }} /></PopoverContent></Popover>{value && <Button type="button" variant="ghost" onClick={() => onChange("")}>Limpar</Button>}</div>
}
