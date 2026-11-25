import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { createPelada, uploadPeladaImage } from "@/api/peladas"
import { applyServerErrors } from "@/lib/form-errors"
import { createPeladaSchema, type CreatePeladaValues } from "@/schemas/pelada"
import { validateImageFile } from "@/schemas/upload"

const DEFAULT_VALUES: CreatePeladaValues = {
  name: "",
  dayOfWeek: "MONDAY",
  timeOfDay: "",
  duration: 1,
  address: "",
  reference: "",
  numberOfTeams: 2,
  playersPerTeam: 5,
  autoCreateDailyEnabled: false,
}

// The create pelada Sheet: the form, the optional photo (kept outside the form, like InfoCard) and the submit
export function useCreatePeladaForm(onCreated: () => void) {
  const navigate = useNavigate()
  const form = useForm<CreatePeladaValues>({
    resolver: zodResolver(createPeladaSchema),
    defaultValues: DEFAULT_VALUES,
  })
  const [image, setImage] = useState<File | null>(null)
  const [imageError, setImageError] = useState<string | null>(null)

  // An empty pick (the file dialog cancelled) clears the photo
  function pickImage(file: File | undefined) {
    const error = file ? validateImageFile(file) : null
    setImageError(error)
    setImage(file && !error ? file : null)
  }

  const submit = form.handleSubmit(async (values) => {
    // An invalid photo stays picked with its error under the field until another one is chosen
    if (imageError) return
    let peladaId: number
    try {
      // The sessions' date and time are in the creator's zone; admins can change it in the settings
      const pelada = await createPelada({ ...values, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone })
      peladaId = pelada.id
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível criar a pelada")
      return
    }
    // The pelada exists from here on: a failed photo never reads as a failed creation, or the user would create
    // it again
    let photoFailed = false
    if (image) {
      try {
        await uploadPeladaImage(peladaId, image)
      } catch {
        photoFailed = true
      }
    }
    if (photoFailed) {
      toast.warning("Pelada criada, mas a foto não foi enviada. Envie de novo nas configurações.")
    } else {
      toast.success("Pelada criada!")
    }
    onCreated()
    navigate(`/pelada/${peladaId}`)
  })

  return { form, imageError, pickImage, submit }
}
