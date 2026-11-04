import { useState } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowDown01Icon, PlusSignIcon } from "@hugeicons/core-free-icons"
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
import { FinanceMemberChecklist } from "@/components/finance/FinanceMemberChecklist"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { applyServerErrors } from "@/lib/form-errors"
import { cn } from "@/lib/utils"
import { getErrorMessage } from "@/lib/errors"
import { formatFinanceMoney, sumFinanceAmounts } from "@/utils/finance"

interface FinanceSettingsProps {
  peladaId: number
  finance: FinanceController
  members: PeladaMember[]
  onTemplate: (template?: FinanceTemplate) => void
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

// The finance settings, one card per subject (Pix, visibility, people, templates), each saving on its own; the audit has its own tab
export function FinanceSettings({ peladaId, finance, members, onTemplate }: FinanceSettingsProps) {
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
          <HugeiconsIcon icon={PlusSignIcon} className="size-[15px]" />
          Novo modelo
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
  // the saved responsáveis first, so they are easy to untick; the order stays put while ticking
  const candidates = members
    .filter((member) => !member.isAdmin)
    .sort((a, b) => Number(settings.managerIds.includes(b.id)) - Number(settings.managerIds.includes(a.id)))
  // the member list is long: it opens on demand, with the current choice summarized above it
  const [open, setOpen] = useState(false)
  const userIds = useWatch({ control: form.control, name: "userIds" })
  const chosen = candidates.filter((member) => userIds.includes(member.id)).map((member) => member.username)
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
        <Collapsible open={open} onOpenChange={setOpen} className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm">
              {chosen.length === 0 ? (
                <span className="text-muted-foreground">Nenhum responsável além dos admins.</span>
              ) : (
                <>
                  <span className="text-muted-foreground">{chosen.length === 1 ? "Responsável: " : `${chosen.length} responsáveis: `}</span>
                  {chosen.join(", ")}
                </>
              )}
            </p>
            <CollapsibleTrigger asChild>
              <Button type="button" variant="outline" size="sm">
                {open ? "Recolher" : "Escolher responsáveis"}
                <HugeiconsIcon icon={ArrowDown01Icon} className={cn("transition-transform", open && "rotate-180")} />
              </Button>
            </CollapsibleTrigger>
          </div>
          <CollapsibleContent>
            <Controller
              control={form.control}
              name="userIds"
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FinanceMemberChecklist idPrefix="finance-manager" members={candidates} selected={field.value} onChange={field.onChange} />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
          </CollapsibleContent>
        </Collapsible>
      )}
      <SettingsRootError message={form.formState.errors.root?.message} />
    </SettingsCard>
  )
}
