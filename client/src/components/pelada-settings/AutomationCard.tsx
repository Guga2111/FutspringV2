import { useState } from "react"
import { toast } from "sonner"
import { updatePelada } from "@/api/peladas"
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field"
import { Switch } from "@/components/ui/switch"
import { getErrorMessage } from "@/lib/errors"
import type { PeladaDetail } from "@/types/pelada"
import { detailFields } from "@/components/pelada-settings/hooks/usePeladaSettings"
import { SettingsCard } from "@/components/SettingsCard"

interface AutomationCardProps {
  pelada: PeladaDetail
  onSaved: (patch: Partial<PeladaDetail>) => void
}

// A preference: saved as soon as it is switched, back to the previous value on error
export function AutomationCard({ pelada, onSaved }: AutomationCardProps) {
  const [pending, setPending] = useState<boolean | null>(null)
  const checked = pending ?? pelada.autoCreateDailyEnabled

  async function toggle(next: boolean) {
    setPending(next)
    try {
      onSaved(detailFields(await updatePelada(pelada.id, { autoCreateDailyEnabled: next })))
      toast.success(next ? "Criação automática ativada" : "Criação automática desativada")
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível salvar a preferência"))
    } finally {
      setPending(null)
    }
  }

  return (
    <SettingsCard id="automacao" title="Automação" description="Preferências são salvas automaticamente.">
      <Field orientation="horizontal" data-disabled={pending !== null}>
        <FieldContent>
          <FieldLabel htmlFor="settings-autoCreate">Criar a próxima sessão automaticamente</FieldLabel>
          <FieldDescription>Na véspera do dia da pelada, a sessão da semana é criada sozinha, no horário da pelada.</FieldDescription>
        </FieldContent>
        <Switch id="settings-autoCreate" checked={checked} disabled={pending !== null} onCheckedChange={toggle} />
      </Field>
    </SettingsCard>
  )
}
