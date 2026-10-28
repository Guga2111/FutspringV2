import { UserMinus } from "lucide-react"
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
import type { PeladaMember } from "@/types/pelada"

interface RemovePlayerDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  // the members the admin can remove (the creator is left out by the caller)
  members: PeladaMember[]
  onConfirm: (member: PeladaMember) => void
}

// ⌘K → "Remover Jogador": pick a member, then the page asks for confirmation
export function RemovePlayerDialog({ open, onOpenChange, members, onConfirm }: RemovePlayerDialogProps) {
  return (
    <CommandDialog open={open} onOpenChange={onOpenChange}>
      <CommandInput placeholder="Pesquisar jogador..." />
      <CommandList>
        <CommandEmpty>Nenhum jogador encontrado.</CommandEmpty>
        <CommandGroup heading="Remover jogador">
          {members.map((m) => (
            <CommandItem
              key={m.id}
              value={`${m.username} ${m.id}`}
              onSelect={() => onConfirm(m)}
              className="text-destructive data-[selected=true]:text-destructive"
            >
              <UserMinus />
              {m.username}
            </CommandItem>
          ))}
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  )
}
