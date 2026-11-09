import { PlayerAvatar } from "@/components/PlayerAvatar"
import type { PublicUser } from "@/types/user"

// Who invited and when, under an invitation's pelada (the invitation page)
export function InvitationDetails({ sender, rows }: { sender: PublicUser; rows: { label: string; value: string }[] }) {
  return (
    <dl className="grid grid-cols-1 gap-3 rounded-tile bg-card-elevated p-4 sm:grid-cols-2">
      <div className="flex flex-col gap-1 sm:col-span-2">
        <dt className="text-body-sm text-muted-foreground">Convidado por</dt>
        <dd className="flex items-center gap-2 text-body font-medium">
          <PlayerAvatar userId={sender.id} username={sender.username} image={sender.image} deleted={sender.isDeleted} className="size-7 text-overline" />
          {sender.username}
        </dd>
      </div>
      {rows.map((row) => (
        <div key={row.label} className="flex flex-col gap-1">
          <dt className="text-body-sm text-muted-foreground">{row.label}</dt>
          <dd className="text-body font-medium tabular-nums">{row.value}</dd>
        </div>
      ))}
    </dl>
  )
}
