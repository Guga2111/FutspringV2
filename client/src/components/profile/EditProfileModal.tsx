import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Camera, Star } from "lucide-react"
import { toast } from "sonner"
import { updateUser, uploadBackgroundImage, uploadUserImage } from "@/api/users"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { getErrorMessage } from "@/lib/errors"
import { applyServerErrors } from "@/lib/form-errors"
import { cn, getFileUrl, getInitials } from "@/lib/utils"
import { IMAGE_ACCEPT, validateImageFile } from "@/schemas/upload"
import { profileSchema, type ProfileValues } from "@/schemas/user"
import { EDITABLE_POSITIONS, positionLabel, type ProfileDTO } from "@/types/user"

interface EditProfileModalProps {
  profile: ProfileDTO
  onClose: () => void
  onProfileUpdated: (updated: ProfileDTO) => void
}

// Select can't hold an empty value, so "no position" uses a sentinel
const NO_POSITION = "__none__"

function toFormPosition(position: string | null): ProfileValues["position"] {
  return (EDITABLE_POSITIONS as readonly string[]).includes(position ?? "")
    ? (position as ProfileValues["position"])
    : ""
}

export default function EditProfileModal({ profile, onClose, onProfileUpdated }: EditProfileModalProps) {
  const [uploading, setUploading] = useState<"avatar" | "background" | null>(null)
  const [current, setCurrent] = useState(profile)
  const form = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      username: profile.username,
      position: toFormPosition(profile.position),
      stars: profile.stars,
    },
  })

  async function handleImage(kind: "avatar" | "background", file: File | undefined) {
    if (!file) return
    const invalid = validateImageFile(file)
    if (invalid) {
      toast.error(invalid)
      return
    }
    setUploading(kind)
    try {
      const updated = kind === "avatar"
        ? await uploadUserImage(profile.id, file)
        : await uploadBackgroundImage(profile.id, file)
      setCurrent(updated)
      onProfileUpdated(updated)
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível enviar a imagem"))
    } finally {
      setUploading(null)
    }
  }

  async function onSubmit(values: ProfileValues) {
    try {
      const updated = await updateUser(profile.id, values)
      form.reset(values)
      onProfileUpdated(updated)
      toast.success("Perfil atualizado")
      onClose()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível salvar o perfil")
    }
  }

  const submitting = form.formState.isSubmitting
  const backgroundUrl = getFileUrl(current.backgroundImage)

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-w-md gap-0 overflow-hidden p-0">
        <div className="relative h-[120px] bg-gradient-primary">
          {backgroundUrl && (
            <img src={backgroundUrl} alt="" className="absolute inset-0 size-full object-cover" />
          )}
          <label
            htmlFor="background-input"
            className="absolute bottom-2 right-2 flex cursor-pointer items-center gap-1 rounded-full bg-background/80 px-3 py-1 text-xs font-medium text-foreground backdrop-blur hover:bg-background"
          >
            <Camera className="size-3.5" />
            {uploading === "background" ? "Enviando..." : "Alterar fundo"}
          </label>
          <Input
            id="background-input"
            type="file"
            accept={IMAGE_ACCEPT}
            className="sr-only"
            disabled={uploading !== null}
            onChange={(e) => {
              void handleImage("background", e.target.files?.[0])
              e.target.value = ""
            }}
          />
        </div>

        <div className="-mt-10 px-6">
          <label htmlFor="avatar-input" className="group relative block w-fit cursor-pointer" aria-label="Alterar foto">
            <Avatar className="size-20 border-4 border-background">
              <AvatarImage src={getFileUrl(current.image)} alt={current.username} />
              <AvatarFallback className="text-lg font-bold">{getInitials(current.username)}</AvatarFallback>
            </Avatar>
            <span className="absolute bottom-0 right-0 flex size-7 items-center justify-center rounded-full border bg-background">
              <Camera className="size-3.5" />
            </span>
          </label>
          <Input
            id="avatar-input"
            type="file"
            accept={IMAGE_ACCEPT}
            className="sr-only"
            disabled={uploading !== null}
            onChange={(e) => {
              void handleImage("avatar", e.target.files?.[0])
              e.target.value = ""
            }}
          />
        </div>

        <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="flex flex-col gap-6 p-6 pt-4">
          <DialogHeader>
            <DialogTitle>Editar perfil</DialogTitle>
            <DialogDescription className="sr-only">Nome de usuário, posição e estrelas</DialogDescription>
          </DialogHeader>
          <FieldGroup className="gap-5">
            <Controller
              name="username"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="username">Nome de usuário</FieldLabel>
                  <Input {...field} id="username" autoComplete="username" aria-invalid={fieldState.invalid} />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <Controller
              name="position"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="position">Posição</FieldLabel>
                  <Select
                    value={field.value === "" ? NO_POSITION : field.value}
                    onValueChange={(value) => field.onChange(value === NO_POSITION ? "" : value)}
                  >
                    <SelectTrigger id="position" aria-invalid={fieldState.invalid}>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value={NO_POSITION}>Sem posição</SelectItem>
                        {EDITABLE_POSITIONS.map((p) => (
                          <SelectItem key={p} value={p}>
                            {positionLabel[p]}
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
              name="stars"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel id="stars-label">Estrelas</FieldLabel>
                  <div role="radiogroup" aria-labelledby="stars-label" className="flex gap-1">
                    {[1, 2, 3, 4, 5].map((i) => (
                      <Button
                        key={i}
                        type="button"
                        variant="ghost"
                        size="icon"
                        role="radio"
                        aria-checked={field.value === i}
                        aria-label={`${i} estrela${i > 1 ? "s" : ""}`}
                        onClick={() => field.onChange(i)}
                      >
                        <Star className={cn("size-6 text-gold", i <= field.value && "fill-current")} />
                      </Button>
                    ))}
                  </div>
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
          </FieldGroup>
          {form.formState.errors.root && (
            <Alert variant="destructive">
              <AlertDescription>{form.formState.errors.root.message}</AlertDescription>
            </Alert>
          )}
          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancelar
            </Button>
            <Button type="submit" variant="gradient" disabled={submitting || !form.formState.isDirty}>
              {submitting ? "Salvando..." : "Salvar"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
