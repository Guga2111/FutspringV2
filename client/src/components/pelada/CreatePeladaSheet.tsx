import { useState, type FormEvent } from "react"
import { Controller, useWatch } from "react-hook-form"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowDown01Icon, Location01Icon } from "@hugeicons/core-free-icons"
import { DAYS_OF_WEEK } from "@/lib/constants"
import { IMAGE_ACCEPT } from "@/schemas/upload"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { cn } from "@/lib/utils"
import type { CreatePeladaValues } from "@/schemas/pelada"
import { useCreatePeladaForm } from "@/components/pelada/hooks/useCreatePeladaForm"

interface CreatePeladaSheetProps {
  onClose: () => void
  onCreated: () => void
}

export function CreatePeladaSheet({ onClose, onCreated }: CreatePeladaSheetProps) {
  const { form, imageError, pickImage, submit } = useCreatePeladaForm(onCreated)
  const [formatOpen, setFormatOpen] = useState(false)
  const [optionsOpen, setOptionsOpen] = useState(false)
  const [duration, numberOfTeams, playersPerTeam, autoCreate, address] = useWatch({
    control: form.control,
    name: ["duration", "numberOfTeams", "playersPerTeam", "autoCreateDailyEnabled", "address"],
  })
  const formatSummary = [
    Number.isInteger(numberOfTeams) && numberOfTeams >= 2 && numberOfTeams <= 10 ? `${numberOfTeams} times` : "Times a definir",
    Number.isInteger(playersPerTeam) && playersPerTeam >= 2 && playersPerTeam <= 20 ? `${playersPerTeam} por time` : "Jogadores a definir",
    Number.isFinite(duration) && duration > 0 ? `${duration.toLocaleString("pt-BR")} h` : "Duração a definir",
  ].join(" · ")

  // Hidden fields keep their values. Validation reveals their section before focusing the first error.
  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    await submit(event)
    const fields: (keyof CreatePeladaValues)[] = ["name", "dayOfWeek", "timeOfDay", "duration", "numberOfTeams", "playersPerTeam", "address", "reference", "autoCreateDailyEnabled"]
    const invalid = fields.filter((name) => form.getFieldState(name).error)
    if (invalid.some((name) => ["duration", "numberOfTeams", "playersPerTeam"].includes(name))) setFormatOpen(true)
    if (imageError || invalid.some((name) => ["address", "reference", "autoCreateDailyEnabled"].includes(name))) setOptionsOpen(true)
    if (invalid[0]) requestAnimationFrame(() => form.setFocus(invalid[0]))
    else if (imageError) requestAnimationFrame(() => document.getElementById("cp-image")?.focus())
  }

  const submitting = form.formState.isSubmitting
  const rootError = form.formState.errors.root

  return (
    <Sheet open onOpenChange={(open) => !open && !submitting && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-md">
        <SheetHeader className="px-6 pb-4 pt-6">
          <SheetTitle className="text-xl font-semibold">Criar Pelada</SheetTitle>
          <SheetDescription className="text-sm text-muted-foreground">
            Comece pelo nome e horário. Os detalhes podem ser ajustados depois.
          </SheetDescription>
        </SheetHeader>

        <form onSubmit={onSubmit} noValidate className="flex flex-1 flex-col overflow-hidden">
          <FieldGroup className="min-h-0 flex-1 gap-5 overflow-y-auto px-6 pb-6">
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

            <FieldGroup className="grid grid-cols-2 gap-4">
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
            </FieldGroup>

            <Collapsible open={formatOpen} onOpenChange={setFormatOpen} className="rounded-tile bg-muted">
              <CollapsibleTrigger className="flex min-h-11 w-full items-center gap-3 rounded-tile p-4 text-left transition-colors hover:bg-row-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold">Formato dos jogos</span>
                  <span className="mt-1 block text-sm text-muted-foreground">{formatSummary}</span>
                </span>
                <HugeiconsIcon icon={ArrowDown01Icon} className={cn("size-4 shrink-0 transition-transform", formatOpen && "rotate-180")} />
              </CollapsibleTrigger>
              <CollapsibleContent className="px-4 pb-4">
                <FieldGroup className="gap-4">
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

                  <FieldGroup className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
                  </FieldGroup>

                </FieldGroup>
              </CollapsibleContent>
            </Collapsible>

            <Collapsible open={optionsOpen} onOpenChange={setOptionsOpen} className="rounded-tile bg-muted">
              <CollapsibleTrigger className="flex min-h-11 w-full items-center gap-3 rounded-tile p-4 text-left transition-colors hover:bg-row-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold">Outras opções</span>
                  <span className="mt-1 block text-sm text-muted-foreground">{address ? "Local definido" : "Local e foto opcionais"} · {autoCreate ? "Sessões automáticas" : "Sessões manuais"}</span>
                </span>
                <HugeiconsIcon icon={ArrowDown01Icon} className={cn("size-4 shrink-0 transition-transform", optionsOpen && "rotate-180")} />
              </CollapsibleTrigger>
              <CollapsibleContent className="px-4 pb-4">
                <FieldGroup className="gap-4">
                  <Controller
                    name="address"
                    control={form.control}
                    render={({ field, fieldState }) => (
                      <Field data-invalid={fieldState.invalid}>
                        <FieldLabel htmlFor="cp-address" className="flex items-center gap-1.5">
                          <HugeiconsIcon icon={Location01Icon} className="size-3.5" />
                          Endereço (opcional)
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
                      aria-describedby="cp-image-help"
                      aria-invalid={imageError !== null}
                      onChange={(e) => pickImage(e.target.files?.[0])}
                    />
                    <FieldDescription id="cp-image-help">JPEG, PNG ou WebP · máx. 5 MB</FieldDescription>
                    {imageError && <FieldError>{imageError}</FieldError>}
                  </Field>

                </FieldGroup>
              </CollapsibleContent>
            </Collapsible>

            {rootError && (
              <Alert variant="destructive">
                <AlertDescription>{rootError.message}</AlertDescription>
              </Alert>
            )}
          </FieldGroup>

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
