import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { updatePelada } from "@/api/peladas"
import { TimeZonePicker } from "@/components/TimeZonePicker"
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { DAYS_OF_WEEK } from "@/lib/constants"
import { peladaScheduleSchema, type PeladaScheduleValues } from "@/schemas/pelada"
import type { PeladaDetail } from "@/types/pelada"
import { detailFields } from "@/components/pelada-settings/hooks/usePeladaSettings"
import { useSettingsSubmit } from "@/components/pelada-settings/hooks/useSettingsSubmit"
import { SettingsCard, SettingsCardFooter, SettingsRootError } from "@/components/pelada-settings/SettingsCard"

interface ScheduleCardProps {
  pelada: PeladaDetail
  onSaved: (patch: Partial<PeladaDetail>) => void
}

export function ScheduleCard({ pelada, onSaved }: ScheduleCardProps) {
  const form = useForm<PeladaScheduleValues>({
    resolver: zodResolver(peladaScheduleSchema),
    defaultValues: {
      dayOfWeek: pelada.dayOfWeek,
      timeOfDay: pelada.timeOfDay.slice(0, 5),
      duration: pelada.duration,
      timeZone: pelada.timeZone,
    },
  })
  const submit = useSettingsSubmit(
    form,
    async (values) => onSaved(detailFields(await updatePelada(pelada.id, values))),
    { success: "Horário atualizado", fallback: "Não foi possível salvar o horário" },
  )

  return (
    <SettingsCard
      id="horario"
      title="Horário"
      description="Vale para as próximas sessões criadas. As sessões já marcadas mantêm a data e o horário."
      onSubmit={submit}
      footer={
        <SettingsCardFooter dirty={form.formState.isDirty} submitting={form.formState.isSubmitting} onCancel={() => form.reset()} />
      }
    >
      <div className="grid grid-cols-1 gap-5 md:grid-cols-3">
        <Controller
          name="dayOfWeek"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="settings-dayOfWeek">Dia da semana</FieldLabel>
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="settings-dayOfWeek" aria-invalid={fieldState.invalid}>
                  <SelectValue placeholder="Selecione um dia" />
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
              <FieldLabel htmlFor="settings-timeOfDay">Horário</FieldLabel>
              <Input {...field} id="settings-timeOfDay" type="time" aria-invalid={fieldState.invalid} />
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />
        <Controller
          name="duration"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="settings-duration">Duração (h)</FieldLabel>
              <Input
                id="settings-duration"
                type="number"
                min="0.5"
                step="0.5"
                inputMode="decimal"
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
            <FieldLabel htmlFor="settings-timeZone">Fuso horário</FieldLabel>
            <TimeZonePicker id="settings-timeZone" value={field.value} onChange={field.onChange} invalid={fieldState.invalid} />
            <FieldDescription>A data e o horário das sessões e a janela dos diaristas usam este fuso.</FieldDescription>
            <FieldError errors={[fieldState.error]} />
          </Field>
        )}
      />
      <SettingsRootError message={form.formState.errors.root?.message} />
    </SettingsCard>
  )
}
