import { HugeiconsIcon } from "@hugeicons/react"
import { MoreHorizontalIcon } from "@hugeicons/core-free-icons"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { invitationStatusLabel, invitationStatusStyle, type Invitation } from "@/types/invitation"
import { formatInvitationDate, invitationTimeLabel } from "@/utils/invitations"

interface InvitationListProps {
  items: Invitation[]
  now: Date
  // pending rows only; history rows have no actions
  onCancel?: (invitation: Invitation) => void
  disabled?: boolean
}

function StatusBadge({ invitation }: { invitation: Invitation }) {
  return (
    <Badge variant="status" className={invitationStatusStyle[invitation.status]}>
      {invitationStatusLabel[invitation.status]}
    </Badge>
  )
}

function RowMenu({ invitation, onCancel, disabled }: { invitation: Invitation; onCancel: (i: Invitation) => void; disabled?: boolean }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" disabled={disabled} aria-label={`Ações do convite de ${invitation.recipient.username}`}>
          <HugeiconsIcon icon={MoreHorizontalIcon} />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem className="text-destructive-soft focus:text-destructive-soft" onSelect={() => onCancel(invitation)}>
          Cancelar convite
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

// Invitations of a pelada: a table from md, a list of rows below it
export function InvitationList({ items, now, onCancel, disabled }: InvitationListProps) {
  const actions = (invitation: Invitation) =>
    onCancel && invitation.status === "PENDING" ? <RowMenu invitation={invitation} onCancel={onCancel} disabled={disabled} /> : null

  return (
    <>
      <ul className="flex flex-col md:hidden">
        {items.map((invitation) => (
          <li key={invitation.id} className="flex items-center gap-3 border-b px-4 py-3 last:border-b-0">
            <PlayerAvatar username={invitation.recipient.username} image={invitation.recipient.image} colorId={invitation.recipient.id} className="size-10 text-caption" />
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <div className="flex min-w-0 items-center gap-2">
                <span className="truncate text-body font-medium">{invitation.recipient.username}</span>
                <StatusBadge invitation={invitation} />
              </div>
              <span className="truncate text-body-sm text-muted-foreground">
                por {invitation.sender.username} · {invitationTimeLabel(invitation, now).toLowerCase()}
              </span>
            </div>
            {actions(invitation)}
          </li>
        ))}
      </ul>

      <Table className="hidden md:table">
        <TableHeader className="bg-card-elevated">
          <TableRow className="hover:bg-transparent">
            <TableHead className="h-10 px-6">Jogador</TableHead>
            <TableHead className="h-10">Convidado por</TableHead>
            <TableHead className="h-10">Enviado</TableHead>
            <TableHead className="h-10">Situação</TableHead>
            <TableHead className="h-10">Status</TableHead>
            <TableHead className="h-10 w-14 px-6"><span className="sr-only">Ações</span></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.map((invitation) => (
            <TableRow key={invitation.id} className="hover:bg-row-hover">
              <TableCell className="px-6 py-3">
                <div className="flex min-w-0 items-center gap-3">
                  <PlayerAvatar username={invitation.recipient.username} image={invitation.recipient.image} colorId={invitation.recipient.id} className="size-8 text-caption" />
                  <span className="truncate text-body font-medium">{invitation.recipient.username}</span>
                </div>
              </TableCell>
              <TableCell className="py-3 text-body text-muted-foreground">{invitation.sender.username}</TableCell>
              <TableCell className="whitespace-nowrap py-3 text-body tabular-nums text-muted-foreground">
                {formatInvitationDate(invitation.createdAt)}
              </TableCell>
              <TableCell className="whitespace-nowrap py-3 text-body text-muted-foreground">{invitationTimeLabel(invitation, now)}</TableCell>
              <TableCell className="py-3"><StatusBadge invitation={invitation} /></TableCell>
              <TableCell className="px-6 py-3 text-right">{actions(invitation)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  )
}
