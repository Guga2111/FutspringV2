import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { deletePelada, removePlayer, setAdmin } from "@/api/peladas"
import { getErrorMessage } from "@/lib/errors"
import type { PeladaDetail, PeladaMember } from "@/types/pelada"

// Admin/creator mutations of the pelada page; the pelada is reloaded after member changes
export function usePeladaActions(pelada: PeladaDetail | null, refetchPelada: () => Promise<void>) {
  const navigate = useNavigate()
  const [deleting, setDeleting] = useState(false)
  const [removing, setRemoving] = useState(false)
  const [togglingAdmin, setTogglingAdmin] = useState<number | null>(null)

  async function removePeladaAndLeave(): Promise<boolean> {
    if (!pelada) return false
    setDeleting(true)
    try {
      await deletePelada(pelada.id)
      toast.success("Pelada excluída")
      navigate("/home")
      return true
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível excluir a pelada"))
      setDeleting(false)
      return false
    }
  }

  async function removeMember(member: PeladaMember): Promise<boolean> {
    if (!pelada) return false
    setRemoving(true)
    try {
      await removePlayer(pelada.id, member.id)
      await refetchPelada()
      toast.success(`${member.username} foi removido da pelada`)
      return true
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível remover o jogador"))
      return false
    } finally {
      setRemoving(false)
    }
  }

  async function toggleAdmin(member: PeladaMember) {
    if (!pelada) return
    setTogglingAdmin(member.id)
    try {
      await setAdmin(pelada.id, member.id, !member.isAdmin)
      await refetchPelada()
      toast.success(member.isAdmin ? `${member.username} não é mais administrador` : `${member.username} agora é administrador`)
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível alterar o administrador"))
    } finally {
      setTogglingAdmin(null)
    }
  }

  return { deleting, removing, togglingAdmin, removePeladaAndLeave, removeMember, toggleAdmin }
}
