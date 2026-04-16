import { useState, useRef } from "react"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { createPelada, uploadPeladaImage } from "@/api/peladas"
import type { CreatePeladaData } from "@/api/peladas"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { MapPin } from "lucide-react"

const DAYS_OF_WEEK = ["Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sabado", "Domingo"]

interface CreatePeladaModalProps {
  onClose: () => void
  onCreated: () => void
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

export default function CreatePeladaModal({ onClose, onCreated }: CreatePeladaModalProps) {
  const navigate = useNavigate()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [form, setForm] = useState<CreatePeladaData>({
    name: "",
    dayOfWeek: "Segunda",
    timeOfDay: "",
    duration: 1,
    address: "",
    reference: "",
    autoCreateDailyEnabled: false,
    numberOfTeams: 2,
    playersPerTeam: 5,
  })
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(false)

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const { name, value, type } = e.target
    if (type === "checkbox") {
      setForm((prev) => ({ ...prev, [name]: (e.target as HTMLInputElement).checked }))
    } else if (name === "duration") {
      setForm((prev) => ({ ...prev, duration: parseFloat(value) || 0 }))
    } else if (name === "numberOfTeams" || name === "playersPerTeam") {
      setForm((prev) => ({ ...prev, [name]: parseInt(value) || 2 }))
    } else {
      setForm((prev) => ({ ...prev, [name]: value }))
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      toast.error("A imagem deve ter menos de 5MB")
      e.target.value = ""
      return
    }
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      toast.error("Somente imagens JPEG, PNG ou WebP são permitidas")
      e.target.value = ""
      return
    }
    setImageFile(file)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    try {
      const pelada = await createPelada(form)
      if (imageFile) {
        await uploadPeladaImage(pelada.id, imageFile)
      }
      toast.success("Pelada criada!")
      onCreated()
      navigate(`/pelada/${pelada.id}`)
    } catch (err: unknown) {
      const message = extractErrorMessage(err) ?? "Falha ao criar pelada"
      toast.error(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-md rounded-2xl p-0 gap-0 overflow-hidden">
        <DialogHeader className="px-6 pt-6 pb-4">
          <DialogTitle className="text-xl font-semibold">Criar Pelada</DialogTitle>
          <DialogDescription className="text-sm text-muted-foreground">
            Preencha em detalhes para criar seus jogos semanais.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="px-6 pb-6 space-y-5">
          {/* Name */}
          <div className="space-y-1.5">
            <Label htmlFor="cp-name">Nome *</Label>
            <Input
              id="cp-name"
              name="name"
              type="text"
              required
              placeholder="Jogasse Onde?"
              value={form.name}
              onChange={handleChange}
              className="focus-visible:ring-green-500"
            />
          </div>

          {/* Day + Time — 2-column */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="cp-dayOfWeek">Dia Da Semana *</Label>
              <Select
                value={form.dayOfWeek}
                onValueChange={(v) => setForm((prev) => ({ ...prev, dayOfWeek: v }))}
              >
                <SelectTrigger id="cp-dayOfWeek" className="focus-visible:ring-green-500 focus:ring-green-500">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {DAYS_OF_WEEK.map((day) => (
                    <SelectItem key={day} value={day}>{day}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="cp-timeOfDay">Horário *</Label>
              <Input
                id="cp-timeOfDay"
                name="timeOfDay"
                type="time"
                required
                value={form.timeOfDay}
                onChange={handleChange}
                className="focus-visible:ring-green-500"
              />
            </div>
          </div>

          {/* Duration */}
          <div className="space-y-1.5">
            <Label htmlFor="cp-duration">Duração (hora) *</Label>
            <Input
              id="cp-duration"
              name="duration"
              type="number"
              required
              min="0.5"
              step="0.5"
              placeholder="1.5"
              value={form.duration}
              onChange={handleChange}
              className="focus-visible:ring-green-500"
            />
          </div>

          {/* Teams + Players per team — 2-column */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="cp-numberOfTeams">Numero de times *</Label>
              <Input
                id="cp-numberOfTeams"
                name="numberOfTeams"
                type="number"
                required
                min="2"
                step="1"
                value={form.numberOfTeams}
                onChange={handleChange}
                className="focus-visible:ring-green-500"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="cp-playersPerTeam">Jogadores por time *</Label>
              <Input
                id="cp-playersPerTeam"
                name="playersPerTeam"
                type="number"
                required
                min="2"
                step="1"
                value={form.playersPerTeam}
                onChange={handleChange}
                className="focus-visible:ring-green-500"
              />
            </div>
          </div>

          {/* Address */}
          <div className="space-y-1.5">
            <Label htmlFor="cp-address" className="flex items-center gap-1.5"><MapPin className="size-3.5" />Endereço</Label>
            <Input
              id="cp-address"
              name="address"
              type="text"
              placeholder="Rua da Estrela"
              value={form.address}
              onChange={handleChange}
              className="focus-visible:ring-green-500"
            />
          </div>

          {/* Reference */}
          <div className="space-y-1.5">
            <Label htmlFor="cp-reference">Referencia (Opcional)</Label>
            <Input
              id="cp-reference"
              name="reference"
              type="text"
              placeholder="Ilha do Retiro"
              value={form.reference}
              onChange={handleChange}
              className="focus-visible:ring-green-500"
            />
          </div>

          {/* Auto-create checkbox */}
          <div className="flex items-center gap-3">
            <Checkbox
              id="cp-autoCreate"
              checked={form.autoCreateDailyEnabled}
              onCheckedChange={(checked) =>
                setForm((prev) => ({ ...prev, autoCreateDailyEnabled: !!checked }))
              }
              className="data-[state=checked]:bg-green-600 data-[state=checked]:border-green-600"
            />
            <Label htmlFor="cp-autoCreate" className="cursor-pointer font-normal">
              Permita auto criar sessões semanais.
            </Label>
          </div>

          {/* Image */}
          <div className="space-y-1.5">
            <Label htmlFor="cp-image">Imagem (Opcional)</Label>
            <Input
              id="cp-image"
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={handleFileChange}
              className="cursor-pointer focus-visible:ring-green-500"
            />
            <p className="text-xs text-muted-foreground">JPEG, PNG, ou WebP · max 5MB</p>
          </div>

          {/* Footer */}
          <div className="flex justify-end pt-2">
            <Button type="submit" variant="gradient" className="px-8" disabled={loading}>
              {loading ? "Criando..." : "Criar Pelada"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
