import { useMemo, useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { toast } from "sonner"
import { updatePelada, uploadPeladaImage } from "@/api/peladas"
import { applyServerErrors } from "@/lib/form-errors"
import { makeUpdatePeladaSchema, type UpdatePeladaValues } from "@/schemas/pelada"
import { validateImageFile } from "@/schemas/upload"
import type { PeladaDetail } from "@/types/pelada"

// The "Editar pelada" sheet: react-hook-form + zod (UpdatePeladaRequestDTO) and the submit; the image is
// uploaded after the update and is kept outside the form (it isn't part of the DTO)
export function useEditPeladaForm(pelada: PeladaDetail, onSaved: () => void) {
  const capacity = pelada.numberOfTeams * pelada.playersPerTeam
  const schema = useMemo(() => makeUpdatePeladaSchema(capacity), [capacity])
  const form = useForm<UpdatePeladaValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: pelada.name,
      dayOfWeek: pelada.dayOfWeek,
      timeOfDay: pelada.timeOfDay.slice(0, 5),
      duration: pelada.duration,
      address: pelada.address ?? "",
      reference: pelada.reference ?? "",
      autoCreateDailyEnabled: pelada.autoCreateDailyEnabled,
      timeZone: pelada.timeZone,
      maxMensalistas: pelada.maxMensalistas,
    },
  })
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imageError, setImageError] = useState<string | null>(null)

  function pickImage(file: File | null) {
    const error = file ? validateImageFile(file) : null
    setImageError(error)
    setImageFile(error ? null : file)
  }

  async function onSubmit(values: UpdatePeladaValues) {
    try {
      await updatePelada(pelada.id, values)
      if (imageFile) await uploadPeladaImage(pelada.id, imageFile)
      toast.success("Pelada atualizada!")
      onSaved()
    } catch (error) {
      applyServerErrors(form, error, "Não foi possível atualizar a pelada")
    }
  }

  return {
    form,
    capacity,
    mensalistaCount: pelada.members.filter((m) => m.isMensalista).length,
    imageError,
    pickImage,
    submit: form.handleSubmit(onSubmit),
  }
}
