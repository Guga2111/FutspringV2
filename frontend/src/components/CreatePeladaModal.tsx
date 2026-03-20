import { useState, useRef } from "react"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { createPelada, uploadPeladaImage } from "@/api/peladas"
import type { CreatePeladaData } from "@/api/peladas"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

const DAYS_OF_WEEK = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

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
    dayOfWeek: "Monday",
    timeOfDay: "",
    duration: 1,
    address: "",
    reference: "",
    autoCreateDailyEnabled: false,
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
      toast.error("Image must be under 5MB")
      e.target.value = ""
      return
    }
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      toast.error("Only JPEG, PNG, or WebP images are allowed")
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
      toast.success("Pelada created!")
      onCreated()
      navigate(`/pelada/${pelada.id}`)
    } catch (err: unknown) {
      const message = extractErrorMessage(err) ?? "Failed to create pelada"
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
          <h2 className="text-lg font-semibold">Create Pelada</h2>
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
            <Label htmlFor="cp-name">Name *</Label>
            <Input
              id="cp-name"
              name="name"
              type="text"
              required
              placeholder="Friday Night Football"
              value={form.name}
              onChange={handleChange}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="cp-dayOfWeek">Day of Week *</Label>
            <select
              id="cp-dayOfWeek"
              name="dayOfWeek"
              required
              value={form.dayOfWeek}
              onChange={handleChange}
              className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            >
              {DAYS_OF_WEEK.map((day) => (
                <option key={day} value={day}>{day}</option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="cp-timeOfDay">Time *</Label>
            <Input
              id="cp-timeOfDay"
              name="timeOfDay"
              type="time"
              required
              value={form.timeOfDay}
              onChange={handleChange}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="cp-duration">Duration (hours) *</Label>
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
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="cp-address">Address</Label>
            <Input
              id="cp-address"
              name="address"
              type="text"
              placeholder="Rua das Flores, 123"
              value={form.address}
              onChange={handleChange}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="cp-reference">Reference</Label>
            <Input
              id="cp-reference"
              name="reference"
              type="text"
              placeholder="Near the park"
              value={form.reference}
              onChange={handleChange}
            />
          </div>

          <div className="flex items-center gap-3">
            <input
              id="cp-autoCreate"
              name="autoCreateDailyEnabled"
              type="checkbox"
              checked={form.autoCreateDailyEnabled}
              onChange={handleChange}
              className="h-4 w-4 rounded border-input"
            />
            <Label htmlFor="cp-autoCreate" className="cursor-pointer">
              Auto-create daily sessions
            </Label>
          </div>

          <div className="space-y-2">
            <Label htmlFor="cp-image">Image (optional)</Label>
            <Input
              id="cp-image"
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={handleFileChange}
              className="cursor-pointer"
            />
            <p className="text-xs text-muted-foreground">JPEG, PNG, or WebP · max 5MB</p>
          </div>

          <div className="flex gap-3 pt-2">
            <Button type="button" variant="outline" className="flex-1" onClick={onClose} disabled={loading}>
              Cancel
            </Button>
            <Button type="submit" className="flex-1" disabled={loading}>
              {loading ? "Creating..." : "Create Pelada"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
