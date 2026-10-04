import { useState } from "react"
import { toast } from "sonner"
import { addPlayer } from "@/api/peladas"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { getErrorMessage } from "@/lib/errors"
import { getFileUrl, getInitials } from "@/lib/utils"
import { getPositionLabel, type PublicUser } from "@/types/user"
import { MIN_SEARCH_LENGTH, useUserSearch } from "@/components/pelada/hooks/useUserSearch"

export function AddPlayerDialog({
  peladaId,
  existingMemberIds,
  onClose,
  onAdded,
}: {
  peladaId: number
  existingMemberIds: Set<number>
  onClose: () => void
  onAdded: () => void
}) {
  const [query, setQuery] = useState("")
  const [adding, setAdding] = useState<number | null>(null)
  const { users, searching, failed, tooShort } = useUserSearch(query)

  async function handleAdd(user: PublicUser) {
    setAdding(user.id)
    try {
      await addPlayer(peladaId, user.id)
      toast.success(`${user.username} foi adicionado à pelada`)
      onAdded()
      onClose()
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível adicionar o jogador"))
    } finally {
      setAdding(null)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Adicionar jogador</DialogTitle>
          <DialogDescription>Busque pelo nome de usuário ou pelo e-mail.</DialogDescription>
        </DialogHeader>
        <Input
          aria-label="Buscar jogador"
          placeholder="Nome de usuário ou e-mail"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          autoFocus
        />
        <div className="flex max-h-64 flex-col gap-1 overflow-y-auto">
          {tooShort && (
            <p className="py-2 text-sm text-muted-foreground">Digite pelo menos {MIN_SEARCH_LENGTH} caracteres.</p>
          )}
          {searching && <p className="py-2 text-sm text-muted-foreground">Buscando...</p>}
          {failed && <p className="py-2 text-sm text-destructive">Não foi possível buscar agora.</p>}
          {users.map((user) => {
            const alreadyMember = existingMemberIds.has(user.id)
            const position = getPositionLabel(user.position)
            return (
              <div key={user.id} className="flex items-center gap-3 rounded-md p-2 hover:bg-muted">
                <Avatar className="size-8">
                  <AvatarImage src={getFileUrl(user.image)} alt={user.username} />
                  <AvatarFallback className="text-xs font-semibold">{getInitials(user.username)}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{user.username}</p>
                  {position && <p className="truncate text-xs text-muted-foreground">{position}</p>}
                </div>
                {alreadyMember ? (
                  <Badge variant="secondary">Membro</Badge>
                ) : (
                  <Button size="sm" disabled={adding !== null} onClick={() => handleAdd(user)}>
                    {adding === user.id ? "Adicionando..." : "Adicionar"}
                  </Button>
                )}
              </div>
            )
          })}
          {!searching && !failed && !tooShort && query.trim() && users.length === 0 && (
            <p className="py-4 text-center text-sm text-muted-foreground">Nenhum usuário encontrado</p>
          )}
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
