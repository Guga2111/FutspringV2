import { Controller } from "react-hook-form"
import { DAYS_OF_WEEK } from "@/lib/constants"
import type { PeladaDetail } from "@/types/pelada"
import { useEditPeladaForm } from "@/components/pelada/hooks/useEditPeladaForm"
import { TimeZonePicker } from "@/components/TimeZonePicker"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription, SheetFooter } from "@/components/ui/sheet"
import { IMAGE_ACCEPT } from "@/schemas/upload"

interface EditPeladaModalProps {
  pelada: PeladaDetail
  onClose: () => void
  onUpdated: () => void
}

export default function EditPeladaModal({ pelada, onClose, onUpdated }: EditPeladaModalProps) {
  const { form, capacity, mensalistaCount, imageError, pickImage, submit } = useEditPeladaForm(pelada, () => {
    onUpdated()
    onClose()
  })
  const submitting = form.formState.isSubmitting

  return (
    <Sheet open onOpenChange={(open) => !open && !submitting && onClose()}>
      <SheetContent side="right" className="flex flex-col p-0 gap-0 w-full sm:max-w-md">
        <SheetHeader className="px-6 pt-6 pb-4">
          <SheetTitle className="text-xl font-semibold">Editar Pelada</SheetTitle>
          <SheetDescription className="text-sm text-muted-foreground">
            Atualize os detalhes da sua pelada.
          </SheetDescription>
        </SheetHeader>

        <form onSubmit={submit} noValidate className="flex flex-col flex-1 overflow-hidden">
          <div className="flex-1 overflow-y-auto px-6 pb-4">
            <FieldGroup className="gap-5">
              <Controller
                name="name"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="ep-name">Nome</FieldLabel>
                    <Input {...field} id="ep-name" placeholder="Jogasse Onde?" aria-invalid={fieldState.invalid} />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />

              <Controller
                name="dayOfWeek"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="ep-dayOfWeek">Dia da semana</FieldLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="ep-dayOfWeek" aria-invalid={fieldState.invalid}>
                        <SelectValue placeholder="Selecione um dia" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {DAYS_OF_WEEK.map((day) => (
                            <SelectItem key={day.value} value={day.value}>{day.label}</SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />

              <div className="grid grid-cols-2 gap-3">
                <Controller
                  name="timeOfDay"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Field data-invalid={fieldState.invalid}>
                      <FieldLabel htmlFor="ep-timeOfDay">Horário</FieldLabel>
                      <Input {...field} id="ep-timeOfDay" type="time" aria-invalid={fieldState.invalid} />
                      <FieldError errors={[fieldState.error]} />
                    </Field>
                  )}
                />
                <Controller
                  name="duration"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Field data-invalid={fieldState.invalid}>
                      <FieldLabel htmlFor="ep-duration">Duração (h)</FieldLabel>
                      <Input
                        id="ep-duration"
                        type="number"
                        min="0.5"
                        step="0.5"
                        name={field.name}
                        ref={field.ref}
                        onBlur={field.onBlur}
                        value={Number.isNaN(field.value) ? "" : field.value}
                        onChange={(e) => field.onChange(e.target.valueAsNumber)}
                        aria-invalid={fieldState.invalid}
                      />
                      <FieldError errors={[fieldState.error]} />
                    </Field>
                  )}
                />
              </div>

              <Controller
                name="timeZone"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="ep-timeZone">Fuso horário</FieldLabel>
                    <TimeZonePicker id="ep-timeZone" value={field.value} onChange={field.onChange} invalid={fieldState.invalid} />
                    <FieldDescription>A data e o horário das sessões e a janela dos diaristas usam este fuso.</FieldDescription>
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />

              <Controller
                name="maxMensalistas"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="ep-maxMensalistas">Mensalistas</FieldLabel>
                    <Input
                      id="ep-maxMensalistas"
                      type="number"
                      min="0"
                      max={capacity}
                      step="1"
                      name={field.name}
                      ref={field.ref}
                      onBlur={field.onBlur}
                      value={Number.isNaN(field.value) ? "" : field.value}
                      onChange={(e) => field.onChange(e.target.valueAsNumber)}
                      aria-invalid={fieldState.invalid}
                    />
                    <FieldDescription>
                      {mensalistaCount} {mensalistaCount === 1 ? "mensalista hoje" : "mensalistas hoje"} · máximo {capacity} vagas
                    </FieldDescription>
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />

              <Controller
                name="address"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="ep-address">Endereço</FieldLabel>
                    <Input {...field} id="ep-address" placeholder="Av Agamenon Magalhães" aria-invalid={fieldState.invalid} />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />

              <Controller
                name="reference"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="ep-reference">Referência</FieldLabel>
                    <Input {...field} id="ep-reference" placeholder="Ilha do Retiro" aria-invalid={fieldState.invalid} />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />

              <Controller
                name="autoCreateDailyEnabled"
                control={form.control}
                render={({ field }) => (
                  <Field orientation="horizontal">
                    <Checkbox id="ep-autoCreate" checked={field.value} onCheckedChange={(checked) => field.onChange(checked === true)} />
                    <FieldLabel htmlFor="ep-autoCreate" className="cursor-pointer font-normal">
                      Auto-criar diárias
                    </FieldLabel>
                  </Field>
                )}
              />

              <Field data-invalid={imageError !== null}>
                <FieldLabel htmlFor="ep-image">Recolocar imagem (opcional)</FieldLabel>
                <Input
                  id="ep-image"
                  type="file"
                  accept={IMAGE_ACCEPT}
                  onChange={(e) => pickImage(e.target.files?.[0] ?? null)}
                  aria-invalid={imageError !== null}
                  className="cursor-pointer file:text-primary"
                />
                <FieldDescription>JPEG, PNG ou WebP · máx. 5 MB</FieldDescription>
                {imageError && <FieldError>{imageError}</FieldError>}
              </Field>
            </FieldGroup>

            {form.formState.errors.root && (
              <Alert variant="destructive" className="mt-5">
                <AlertDescription>{form.formState.errors.root.message}</AlertDescription>
              </Alert>
            )}
          </div>

          <SheetFooter className="px-6 py-4 border-t gap-3">
            <Button type="button" variant="outline" className="flex-1" onClick={onClose} disabled={submitting}>
              Cancelar
            </Button>
            <Button variant="gradient" type="submit" className="flex-1" disabled={submitting}>
              {submitting ? "Salvando..." : "Salvar Mudanças"}
            </Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
