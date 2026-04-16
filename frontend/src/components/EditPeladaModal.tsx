import { useState, useRef } from "react"
import { toast } from "sonner"
import { updatePelada, uploadPeladaImage } from "@/api/peladas"
import type { UpdatePeladaData } from "@/api/peladas"
import type { PeladaDetail } from "@/types/pelada"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"

const DAYS_OF_WEEK = ["Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sabado", "Domingo"]

interface EditPeladaModalProps {
  pelada: PeladaDetail
  onClose: () => void
  onUpdated: () => void
}

function extractErrorMessage(err: unknown): string | null {
  if (
    err &&
    typeof err === "object" &&
    "response" in err &&
    err.response &&
    typeof err.response === "object" &&
    "data" in err.response &&
    err.response.data &&
    typeof err.response.data === "object" &&
    "message" in err.response.data &&
    typeof (err.response.data as { message: unknown }).message === "string"
  ) {
    return (err.response.data as { message: string }).message
  }
  return null
}

export default function EditPeladaModal({ pelada, onClose, onUpdated }: EditPeladaModalProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [form, setForm] = useState<UpdatePeladaData>({
    name: pelada.name,
    dayOfWeek: pelada.dayOfWeek,
    timeOfDay: pelada.timeOfDay,
    duration: pelada.duration,
    address: pelada.address ?? "",
    reference: pelada.reference ?? "",
    autoCreateDailyEnabled: pelada.autoCreateDailyEnabled,
  })
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(false)

  function handleChange(e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) {
    const { name, value, type } = e.target
    if (type === "checkbox") {
      setForm((prev) => ({ ...prev, [name]: (e.target as HTMLInputElement).checked }))
    } else if (name === "duration") {
      setForm((prev) => ({ ...prev, duration: parseFloat(value) || 0 }))
    } else {
      setForm((prev) => ({ ...prev, [name]: value }))
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      toast.error("Imagens devem ter no máximo 5MB")
      e.target.value = ""
      return
    }
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      toast.error("Somente JPEG, PNG, ou WebP são permitidos")
      e.target.value = ""
      return
    }
    setImageFile(file)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    try {
      await updatePelada(pelada.id, form)
      if (imageFile) {
        await uploadPeladaImage(pelada.id, imageFile)
      }
      toast.success("Pelada atualizada!")
      onUpdated()
      onClose()
    } catch (err: unknown) {
      const message = extractErrorMessage(err) ?? "Falha ao atualizar pelada"
      toast.error(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="bg-background rounded-lg shadow-lg w-full max-w-md mx-4 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-lg font-semibold">Editar Pelada</h2>
          <button
            onClick={onClose}
            className="text-muted-foreground hover:text-foreground transition-colors"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-4 space-y-4">
          <div className="space-y-2">
            <Label htmlFor="ep-name">Nome *</Label>
            <Input
              id="ep-name"
              name="name"
              type="text"
              required
              placeholder="Jogasse Onde?"
              value={form.name ?? ""}
              onChange={handleChange}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="ep-dayOfWeek">Dia da Semana *</Label>
            <Select
              value={form.dayOfWeek ?? "Segunda"}
              onValueChange={(value) => setForm((prev) => ({ ...prev, dayOfWeek: value }))}
            >
              <SelectTrigger id="ep-dayOfWeek">
                <SelectValue placeholder="Selecione um dia" />
              </SelectTrigger>
              <SelectContent>
                {DAYS_OF_WEEK.map((day) => (
                  <SelectItem key={day} value={day}>{day}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="ep-timeOfDay">Horário *</Label>
            <Input
              id="ep-timeOfDay"
              name="timeOfDay"
              type="time"
              required
              value={form.timeOfDay ?? ""}
              onChange={handleChange}
              className="[&::-webkit-calendar-picker-indicator]:opacity-70"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="ep-duration">Duração (hora) *</Label>
            <Input
              id="ep-duration"
              name="duration"
              type="number"
              required
              min="0.5"
              step="0.5"
              placeholder="1.5"
              value={form.duration ?? ""}
              onChange={handleChange}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="ep-address">Endereço</Label>
            <Input
              id="ep-address"
              name="address"
              type="text"
              placeholder="Av Agamenon Magalhães"
              value={form.address ?? ""}
              onChange={handleChange}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="ep-reference">Referência</Label>
            <Input
              id="ep-reference"
              name="reference"
              type="text"
              placeholder="Ilha do Retiro"
              value={form.reference ?? ""}
              onChange={handleChange}
            />
          </div>

          <div className="flex items-center gap-3">
            <Checkbox
              id="ep-autoCreate"
              checked={form.autoCreateDailyEnabled ?? false}
              onCheckedChange={(checked) =>
                setForm((prev) => ({ ...prev, autoCreateDailyEnabled: checked === true }))
              }
            />
            <Label htmlFor="ep-autoCreate" className="cursor-pointer">
              Auto-criar diárias
            </Label>
          </div>

          <div className="space-y-2">
            <Label htmlFor="ep-image">Recolocar imagem (opcional)</Label>
            <Input
              id="ep-image"
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={handleFileChange}
              className="cursor-pointer file:text-primary"
            />
            <p className="text-xs text-muted-foreground">JPEG, PNG, ou WebP · max 5MB</p>
          </div>

          <div className="flex gap-3 pt-2">
            <Button type="button" variant="outline" className="flex-1" onClick={onClose} disabled={loading}>
              Cancelar
            </Button>
            <Button variant="gradient" type="submit" className="flex-1" disabled={loading}>
              {loading ? "Salvando..." : "Salvar Mudanças"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
