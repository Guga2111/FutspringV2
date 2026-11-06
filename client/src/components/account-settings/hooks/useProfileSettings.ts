import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { updateUser, uploadUserImage } from "@/api/users"
import { getErrorMessage } from "@/lib/errors"
import { applyServerErrors } from "@/lib/form-errors"
import { validateImageFile } from "@/schemas/upload"
import { profileSchema, type ProfileValues } from "@/schemas/user"
import { useAuth } from "@/hooks/useAuth"
import { EDITABLE_POSITIONS, type ProfileDTO } from "@/types/user"
import type { UserResponseDTO } from "@/types/auth"

function toFormPosition(position: string | null): ProfileValues["position"] {
  return (EDITABLE_POSITIONS as readonly string[]).includes(position ?? "")
    ? (position as ProfileValues["position"])
    : ""
}

const sessionPatch = (profile: ProfileDTO) => ({
  username: profile.username,
  image: profile.image,
  position: profile.position,
})

// The Perfil card: the photo is sent as soon as it is picked; name and position go with "Salvar alterações".
// Both update the session user, so the sidebar shows the change at once.
export function useProfileSettings(user: UserResponseDTO) {
  const { updateUser: updateSessionUser } = useAuth()
  const [uploading, setUploading] = useState(false)
  const form = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { username: user.username, position: toFormPosition(user.position) },
  })

  async function uploadPhoto(file: File | undefined) {
    if (!file) return
    const invalid = validateImageFile(file)
    if (invalid) {
      toast.error(invalid)
      return
    }
    setUploading(true)
    try {
      updateSessionUser(sessionPatch(await uploadUserImage(user.id, file)))
      toast.success("Foto atualizada")
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível enviar a imagem"))
    } finally {
      setUploading(false)
    }
  }

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      const updated = await updateUser(user.id, values)
      form.reset({ username: updated.username, position: toFormPosition(updated.position) })
      updateSessionUser(sessionPatch(updated))
      toast.success("Perfil atualizado")
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível salvar o perfil")
    }
  })

  return { form, onSubmit, uploading, uploadPhoto }
}
