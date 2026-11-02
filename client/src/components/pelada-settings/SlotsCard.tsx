import { Controller, useForm, useWatch, type Control } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { HugeiconsIcon } from "@hugeicons/react"
import { Alert02Icon } from "@hugeicons/core-free-icons"
import { updatePelada } from "@/api/peladas"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { peladaSlotsSchema, type PeladaSlotsValues } from "@/schemas/pelada"
import type { NextDaily, PeladaDetail } from "@/types/pelada"
import { detailFields } from "@/components/pelada-settings/hooks/usePeladaSettings"
import { useSettingsSubmit } from "@/components/pelada-settings/hooks/useSettingsSubmit"
import { SettingsCard, SettingsCardFooter, SettingsRootError } from "@/components/pelada-settings/SettingsCard"

interface SlotsCardProps {
  pelada: PeladaDetail
  nextDaily: NextDaily | null
  onSaved: (patch: Partial<PeladaDetail>) => void
}

// Teams, players per team and the mensalista limit. The new capacity also applies to the sessions already
// scheduled; nobody is removed, so the card warns when the next session would be over the limit.
export function SlotsCard({ pelada, nextDaily, onSaved }: SlotsCardProps) {
  const form = useForm<PeladaSlotsValues>({
    resolver: zodResolver(peladaSlotsSchema),
    defaultValues: {
      numberOfTeams: pelada.numberOfTeams,
      playersPerTeam: pelada.playersPerTeam,
      maxMensalistas: pelada.maxMensalistas,
    },
  })
  const submit = useSettingsSubmit(
    form,
    async (values) => {
      const saved = await updatePelada(pelada.id, values)
      onSaved({ ...detailFields(saved), maxMensalistas: values.maxMensalistas })
    },
    { success: "Vagas atualizadas", fallback: "Não foi possível salvar as vagas" },
  )
  const mensalistaCount = pelada.members.filter((m) => m.isMensalista).length

  return (
    <SettingsCard
      id="vagas"
      title="Vagas"
      description="Vale também para as sessões já marcadas. Ninguém é removido: quem passar das vagas fica acima do limite, e times já sorteados não mudam."
      onSubmit={submit}
      footer={
        <SettingsCardFooter dirty={form.formState.isDirty} submitting={form.formState.isSubmitting} onCancel={() => form.reset()} />
      }
    >
      <div className="grid grid-cols-2 gap-5 md:grid-cols-3">
        <NumberField control={form.control} name="numberOfTeams" label="Times" min={2} max={10} />
        <NumberField control={form.control} name="playersPerTeam" label="Jogadores por time" min={2} max={20} />
        <div className="col-span-2 md:col-span-1">
          <NumberField
            control={form.control}
            name="maxMensalistas"
            label="Limite de mensalistas"
            min={0}
            description={`${mensalistaCount} ${mensalistaCount === 1 ? "mensalista hoje" : "mensalistas hoje"}`}
          />
        </div>
      </div>
      <CapacitySummary control={form.control} savedCapacity={pelada.numberOfTeams * pelada.playersPerTeam} nextDaily={nextDaily} />
      <SettingsRootError message={form.formState.errors.root?.message} />
    </SettingsCard>
  )
}

interface NumberFieldProps {
  control: Control<PeladaSlotsValues>
  name: keyof PeladaSlotsValues
  label: string
  min: number
  max?: number
  description?: string
}

function NumberField({ control, name, label, min, max, description }: NumberFieldProps) {
  const id = `settings-${name}`
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={id}>{label}</FieldLabel>
          <Input
            id={id}
            type="number"
            inputMode="numeric"
            min={min}
            max={max}
            step="1"
            name={field.name}
            ref={field.ref}
            onBlur={field.onBlur}
            value={Number.isNaN(field.value) ? "" : field.value}
            onChange={(e) => field.onChange(e.target.valueAsNumber)}
            aria-invalid={fieldState.invalid}
          />
          {description && <FieldDescription>{description}</FieldDescription>}
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )
}

interface CapacitySummaryProps {
  control: Control<PeladaSlotsValues>
  savedCapacity: number
  nextDaily: NextDaily | null
}

// Live capacity while editing, and the warning when the next session would have more confirmed than spots
function CapacitySummary({ control, savedCapacity, nextDaily }: CapacitySummaryProps) {
  const [teams, players] = useWatch({ control, name: ["numberOfTeams", "playersPerTeam"] })
  const capacity = Number.isFinite(teams) && Number.isFinite(players) ? teams * players : null
  const over = capacity !== null && capacity !== savedCapacity && nextDaily ? nextDaily.confirmedCount - capacity : 0

  return (
    <>
      <p className="text-sm text-muted-foreground">
        Capacidade por sessão:{" "}
        <strong className="font-semibold text-foreground">{capacity === null ? "—" : `${capacity} jogadores`}</strong>
      </p>
      {over > 0 && nextDaily && (
        <Alert>
          <HugeiconsIcon icon={Alert02Icon} />
          <AlertTitle>A próxima sessão vai ficar acima do limite</AlertTitle>
          <AlertDescription>
            Ela tem {nextDaily.confirmedCount} confirmados; com {capacity} vagas, {over}{" "}
            {over === 1 ? "fica" : "ficam"} acima do limite. Ninguém é removido.
          </AlertDescription>
        </Alert>
      )}
    </>
  )
}
