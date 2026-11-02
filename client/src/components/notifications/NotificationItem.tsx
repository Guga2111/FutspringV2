import { HugeiconsIcon } from "@hugeicons/react"
import { formatDistanceToNow } from "date-fns"
import { ptBR } from "date-fns/locale"
import { Button } from "@/components/ui/button"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { cn } from "@/lib/utils"
import type { NotificationResponse } from "@/types/notification"
import { notificationView } from "@/utils/notifications"

interface NotificationItemProps {
  notification: NotificationResponse
  onSelect: (notification: NotificationResponse, href: string | null) => void
}

// One row of the panel: who caused it (or the pelada for system events) with the type's icon, the text and when
export function NotificationItem({ notification, onSelect }: NotificationItemProps) {
  const { parts, href, icon: Icon } = notificationView(notification)
  const unread = notification.readAt === null
  const when = formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true, locale: ptBR })

  return (
    <Button
      variant="ghost"
      onClick={() => onSelect(notification, href)}
      className={cn(
        "h-auto w-full items-start justify-start gap-3 whitespace-normal rounded-tile px-3 py-2.5 text-left font-normal",
        unread && "bg-accent",
      )}
    >
      <span className="relative shrink-0">
        {notification.actor ? (
          <PlayerAvatar username={notification.actor.username} image={notification.actor.image} className="size-9 text-xs" />
        ) : notification.pelada ? (
          <PeladaAvatar name={notification.pelada.name} image={notification.pelada.image} className="size-9 rounded-full text-xs" />
        ) : (
          <span className="flex size-9 items-center justify-center rounded-full bg-avatar-fallback" />
        )}
        <span className="absolute -bottom-0.5 -right-0.5 flex size-[18px] items-center justify-center rounded-full border border-border bg-card text-muted-foreground [&_svg]:size-2.5">
          <HugeiconsIcon icon={Icon} aria-hidden />
        </span>
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="text-[13px] leading-snug text-foreground">
          {parts.map((part, i) =>
            part.bold ? (
              // parts are a fixed sequence per type, so the index is a stable key
              <strong key={i} className="font-semibold">
                {part.text}
              </strong>
            ) : (
              <span key={i}>{part.text}</span>
            ),
          )}
        </span>
        <span className="text-xs text-subtle-foreground">{when}</span>
      </span>
      {unread && <span className="mt-1.5 size-2 shrink-0 rounded-full bg-brand" aria-label="Não lida" />}
    </Button>
  )
}
