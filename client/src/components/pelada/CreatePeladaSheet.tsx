import { Controller } from "react-hook-form"
import { HugeiconsIcon } from "@hugeicons/react"
import { Location01Icon } from "@hugeicons/core-free-icons"
import { DAYS_OF_WEEK } from "@/lib/constants"
import { IMAGE_ACCEPT } from "@/schemas/upload"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { useCreatePeladaForm } from "@/components/pelada/hooks/useCreatePeladaForm"

interface CreatePeladaSheetProps {
  onClose: () => void
  onCreated: () => void
}

export function CreatePeladaSheet({ onClose, onCreated }: CreatePeladaSheetProps) {
  const { form, imageError, pickImage, submit } = useCreatePeladaForm(onCreated)
  const submitting = form.formState.isSubmitting
  const rootError = form.formState.errors.root

  return (
    <Sheet open onOpenChange={(open) => !open && !submitting && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-md">
        <SheetHeader className="px-6 pb-4 pt-6">
          <SheetTitle className="text-xl font-semibold">Criar Pelada</SheetTitle>
          <SheetDescription className="text-sm text-muted-foreground">
            Preencha em detalhes para criar seus jogos semanais.
          </SheetDescription>
        </SheetHeader>

        <form onSubmit={submit} noValidate className="flex flex-1 flex-col overflow-hidden">
          <div className="flex-1 space-y-5 overflow-y-auto px-6">
            <Controller
              name="name"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="cp-name">Nome *</FieldLabel>
                  <Input
                    {...field}
                    id="cp-name"
                    type="text"
                    autoComplete="off"
                    placeholder="Jogasse Onde?"
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              <Controller
                name="dayOfWeek"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="cp-dayOfWeek">Dia da semana *</FieldLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="cp-dayOfWeek" aria-invalid={fieldState.invalid}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {DAYS_OF_WEEK.map((day) => (
                            <SelectItem key={day.value} value={day.value}>
                              {day.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
              <Controller
                name="timeOfDay"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="cp-timeOfDay">Horário *</FieldLabel>
                    <Input {...field} id="cp-timeOfDay" type="time" aria-invalid={fieldState.invalid} />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
            </div>

            <Controller
              name="duration"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="cp-duration">Duração (h) *</FieldLabel>
                  <Input
                    id="cp-duration"
                    type="number"
                    min="0.5"
                    step="0.5"
                    inputMode="decimal"
                    placeholder="1.5"
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

            <div className="grid grid-cols-2 gap-4">
              <Controller
                name="numberOfTeams"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="cp-numberOfTeams">Número de times *</FieldLabel>
                    <Input
                      id="cp-numberOfTeams"
                      type="number"
                      min="2"
                      max="10"
                      step="1"
                      inputMode="numeric"
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
              <Controller
                name="playersPerTeam"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="cp-playersPerTeam">Jogadores por time *</FieldLabel>
                    <Input
                      id="cp-playersPerTeam"
                      type="number"
                      min="2"
                      max="20"
                      step="1"
                      inputMode="numeric"
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
              name="address"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="cp-address" className="flex items-center gap-1.5">
                    <HugeiconsIcon icon={Location01Icon} className="size-3.5" />
                    Endereço
                  </FieldLabel>
                  <Input
                    {...field}
                    id="cp-address"
                    type="text"
                    autoComplete="street-address"
                    placeholder="Rua da Estrela"
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />

            <Controller
              name="reference"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="cp-reference">Referência (opcional)</FieldLabel>
                  <Input
                    {...field}
                    id="cp-reference"
                    type="text"
                    autoComplete="off"
                    placeholder="Ilha do Retiro"
                    aria-invalid={fieldState.invalid}
                  />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />

            <Controller
              name="autoCreateDailyEnabled"
              control={form.control}
              render={({ field }) => (
                <Field orientation="horizontal">
                  <Checkbox
                    id="cp-autoCreate"
                    checked={field.value}
                    onCheckedChange={(checked) => field.onChange(checked === true)}
                    onBlur={field.onBlur}
                    ref={field.ref}
                  />
                  <FieldLabel htmlFor="cp-autoCreate" className="cursor-pointer font-normal">
                    Criar as sessões semanais automaticamente
                  </FieldLabel>
                </Field>
              )}
            />

            <Field data-invalid={imageError !== null}>
              <FieldLabel htmlFor="cp-image">Imagem (opcional)</FieldLabel>
              <Input
                id="cp-image"
                type="file"
                accept={IMAGE_ACCEPT}
                className="cursor-pointer"
                aria-invalid={imageError !== null}
                onChange={(e) => pickImage(e.target.files?.[0])}
              />
              <FieldDescription>JPEG, PNG ou WebP · máx. 5 MB</FieldDescription>
              {imageError && <FieldError>{imageError}</FieldError>}
            </Field>

            {rootError && (
              <Alert variant="destructive">
                <AlertDescription>{rootError.message}</AlertDescription>
              </Alert>
            )}
          </div>

          <SheetFooter className="border-t px-6 py-4">
            <Button type="submit" variant="brand" className="w-full" disabled={submitting}>
              {submitting ? "Criando..." : "Criar Pelada"}
            </Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
