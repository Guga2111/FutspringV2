import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowRight01Icon, PlusSignIcon } from "@hugeicons/core-free-icons"
import { toast } from "sonner"
import * as api from "@/api/finance"
import { financeManagersSchema, financePixSchema, financeVisibilitySchema } from "@/schemas/finance"
import type { FinanceController } from "@/components/finance/hooks/useFinance"
import type { PeladaMember } from "@/types/pelada"
import type { FinanceSettings as FinanceSettingsValues, FinanceTemplate, FinanceVisibility } from "@/types/finance"
import { financeClassificationLabel } from "@/types/finance"
import { FinanceInput } from "@/components/finance/FinanceFields"
import { FinanceQrField } from "@/components/finance/FinanceQrField"
import { SettingsCard, SettingsCardFooter, SettingsRootError } from "@/components/SettingsCard"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { applyServerErrors } from "@/lib/form-errors"
import { getErrorMessage } from "@/lib/errors"
import { formatFinanceMoney, sumFinanceAmounts } from "@/utils/finance"

interface FinanceSettingsProps {
  peladaId: number
  finance: FinanceController
  members: PeladaMember[]
  onTemplate: (template?: FinanceTemplate) => void
  onAudit: () => void
}

const VISIBILITY: Record<FinanceVisibility, { title: string; description: string }> = {
  INDIVIDUAL: {
    title: "Individual",
    description: "Cada membro vê só as próprias cobranças e o Pix para pagar. O caixa fica com os responsáveis.",
  },
  TRANSPARENT: {
    title: "Transparente",
    description: "Os membros veem todas as cobranças publicadas, quem pagou e o saldo do caixa.",
  },
}

// The finance settings, one card per subject (Pix, visibility, people, templates, history), each saving on its own
export function FinanceSettings({ peladaId, finance, members, onTemplate, onAudit }: FinanceSettingsProps) {
  const overview = finance.overview
  if (!overview) return null
  const settings = overview.settings
  return (
    <div className="flex min-w-0 flex-col gap-5">
      <PixCard peladaId={peladaId} finance={finance} settings={settings} />
      <VisibilityCard peladaId={peladaId} finance={finance} settings={settings} />
      {overview.permissions.appointManagers && <ManagersCard peladaId={peladaId} finance={finance} settings={settings} members={members} />}
      <SettingsCard
        id="finance-templates"
        title="Modelos de cobrança"
        description="Os valores que você costuma cobrar, para criar cobranças mais rápido."
      >
        <ul className="flex flex-col rounded-lg border">
          {overview.templates.map((template) => (
            <li key={template.id ?? template.classification} className="flex items-center gap-3 border-b p-3 last:border-b-0">
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 font-medium">
                  {template.name}
                  {template.id === null && <Badge variant="outline">Sugestão</Badge>}
                </p>
                <p className="text-sm text-muted-foreground">
                  {financeClassificationLabel[template.classification]} ·{" "}
                  {formatFinanceMoney(sumFinanceAmounts(template.components.map((component) => component.amount)))} por pessoa
                </p>
              </div>
              <Button variant="ghost" size="sm" onClick={() => onTemplate(template)}>
                Editar
              </Button>
            </li>
          ))}
        </ul>
        <Button variant="outline" className="self-start" onClick={() => onTemplate()}>
          <HugeiconsIcon icon={PlusSignIcon} />
          Novo modelo
        </Button>
      </SettingsCard>
      <SettingsCard
        id="finance-history"
        title="Histórico de alterações"
        description="Quem criou, recebeu, corrigiu ou cancelou cada coisa no financeiro, com o motivo."
      >
        <Button variant="outline" className="self-start" onClick={onAudit}>
          Ver histórico
          <HugeiconsIcon icon={ArrowRight01Icon} />
        </Button>
      </SettingsCard>
    </div>
  )
}

interface CardProps {
  peladaId: number
  finance: FinanceController
  settings: FinanceSettingsValues
}

// the settings PUT takes every field: a card sends its own values and the others as they are saved
const saved = (settings: FinanceSettingsValues) => ({
  visibility: settings.visibility,
  pixKey: settings.pixKey ?? "",
  pixRecipient: settings.pixRecipient ?? "",
  qrId: settings.qrId,
})

function PixCard({ peladaId, finance, settings }: CardProps) {
  const defaults = { pixKey: settings.pixKey ?? "", pixRecipient: settings.pixRecipient ?? "" }
  const form = useForm({ resolver: zodResolver(financePixSchema), defaultValues: defaults })

  async function upload(file: File) {
    try {
      await finance.mutate(
        JSON.stringify({ type: "qr", file: { name: file.name, size: file.size, modified: file.lastModified } }),
        "",
        (command) => api.uploadFinanceQr(peladaId, null, command, file),
      )
      toast.success("QR Code salvo")
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível enviar o QR Code"))
    }
  }

  async function removeQr() {
    try {
      const values = { ...saved(settings), qrId: null }
      await finance.mutate(JSON.stringify({ type: "settings", values }), "", (command) => api.saveFinanceSettings(peladaId, command, values))
      toast.success("QR Code removido")
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível remover o QR Code"))
    }
  }

  return (
    <SettingsCard
      id="finance-pix"
      title="Pix para receber"
      description="Usado nas novas cobranças. As cobranças já criadas mantêm o Pix com que foram criadas."
      onSubmit={form.handleSubmit(async (values) => {
        try {
          const next = { ...saved(settings), ...values }
          await finance.mutate(JSON.stringify({ type: "settings", values: next }), "", (command) => api.saveFinanceSettings(peladaId, command, next))
          form.reset(values)
          toast.success("Pix salvo")
        } catch (error) {
          applyServerErrors(form, error, "Não foi possível salvar o Pix")
        }
      })}
      footer={
        <SettingsCardFooter
          dirty={form.formState.isDirty}
          submitting={form.formState.isSubmitting}
          onCancel={() => form.reset(defaults)}
        />
      }
    >
      <FieldGroup>
        <FinanceInput control={form.control} name="pixKey" label="Chave Pix" placeholder="E-mail, telefone, CPF ou chave aleatória" />
        <FinanceInput control={form.control} name="pixRecipient" label="Nome de quem recebe" placeholder="Como aparece no app do banco" />
      </FieldGroup>
      <FinanceQrField peladaId={peladaId} qrId={settings.qrId} busy={finance.pending} onUpload={upload} onRemove={removeQr} />
      <SettingsRootError message={form.formState.errors.root?.message} />
    </SettingsCard>
  )
}

function VisibilityCard({ peladaId, finance, settings }: CardProps) {
  const defaults = { visibility: settings.visibility }
  const form = useForm({ resolver: zodResolver(financeVisibilitySchema), defaultValues: defaults })
  return (
    <SettingsCard
      id="finance-visibility"
      title="O que os membros veem"
      description="Responsáveis, admins e o criador sempre veem tudo."
      onSubmit={form.handleSubmit(async (values) => {
        try {
          const next = { ...saved(settings), ...values }
          await finance.mutate(JSON.stringify({ type: "settings", values: next }), "", (command) => api.saveFinanceSettings(peladaId, command, next))
          form.reset(values)
          toast.success("Visibilidade salva")
        } catch (error) {
          applyServerErrors(form, error, "Não foi possível salvar a visibilidade")
        }
      })}
      footer={
        <SettingsCardFooter
          dirty={form.formState.isDirty}
          submitting={form.formState.isSubmitting}
          onCancel={() => form.reset(defaults)}
        />
      }
    >
      <Controller
        control={form.control}
        name="visibility"
        render={({ field }) => (
          <RadioGroup value={field.value} onValueChange={field.onChange} className="gap-3" aria-label="Visibilidade do financeiro">
            {(Object.keys(VISIBILITY) as FinanceVisibility[]).map((value) => (
              <FieldLabel
                key={value}
                htmlFor={`finance-visibility-${value}`}
                className="flex w-full cursor-pointer items-start gap-3 rounded-xl border p-4 font-normal has-[[data-state=checked]]:border-brand has-[[data-state=checked]]:bg-brand-muted"
              >
                <RadioGroupItem value={value} id={`finance-visibility-${value}`} className="mt-0.5" />
                <span className="flex flex-col gap-1">
                  <span className="font-medium">{VISIBILITY[value].title}</span>
                  <span className="text-sm text-muted-foreground">{VISIBILITY[value].description}</span>
                </span>
              </FieldLabel>
            ))}
          </RadioGroup>
        )}
      />
      <SettingsRootError message={form.formState.errors.root?.message} />
    </SettingsCard>
  )
}

function ManagersCard({ peladaId, finance, settings, members }: CardProps & { members: PeladaMember[] }) {
  const defaults = { userIds: settings.managerIds }
  const form = useForm({ resolver: zodResolver(financeManagersSchema), defaultValues: defaults })
  // admins (the creator included) already manage the finance; only the other members can be appointed
  const admins = members.filter((member) => member.isAdmin)
  const candidates = members.filter((member) => !member.isAdmin)
  return (
    <SettingsCard
      id="finance-managers"
      title="Responsáveis financeiros"
      description="Membros que cuidam do dinheiro: criam cobranças, recebem e mexem no caixa. Não ganham poderes de admin na pelada."
      onSubmit={form.handleSubmit(async (values) => {
        try {
          await finance.mutate(JSON.stringify({ type: "managers", values }), "", (command) => api.saveFinanceManagers(peladaId, command, values.userIds))
          form.reset(values)
          toast.success("Responsáveis atualizados")
        } catch (error) {
          applyServerErrors(form, error, "Não foi possível salvar os responsáveis")
        }
      })}
      footer={
        <SettingsCardFooter
          dirty={form.formState.isDirty}
          submitting={form.formState.isSubmitting}
          onCancel={() => form.reset(defaults)}
        />
      }
    >
      {admins.length > 0 && (
        <p className="text-sm text-muted-foreground">
          Já têm acesso como admins: <span className="text-foreground">{admins.map((admin) => admin.username).join(", ")}</span>
        </p>
      )}
      {candidates.length === 0 ? (
        <p className="text-sm text-muted-foreground">Todos os membros já são admins.</p>
      ) : (
        <Controller
          control={form.control}
          name="userIds"
          render={({ field, fieldState }) => (
            <FieldGroup className="gap-0 rounded-lg border">
              {candidates.map((member) => (
                <Field key={member.id} orientation="horizontal" className="items-center gap-3 border-b p-3 last:border-b-0">
                  <Checkbox
                    id={`finance-manager-${member.id}`}
                    checked={field.value.includes(member.id)}
                    onCheckedChange={(checked) =>
                      field.onChange(checked ? [...field.value, member.id] : field.value.filter((id) => id !== member.id))
                    }
                  />
                  <PlayerAvatar username={member.username} image={member.image} className="size-8 text-xs" />
                  <FieldLabel htmlFor={`finance-manager-${member.id}`} className="flex-1 cursor-pointer font-normal">
                    {member.username}
                  </FieldLabel>
                </Field>
              ))}
              <FieldError errors={[fieldState.error]} />
            </FieldGroup>
          )}
        />
      )}
      <SettingsRootError message={form.formState.errors.root?.message} />
    </SettingsCard>
  )
}
