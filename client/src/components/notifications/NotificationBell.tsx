import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { Notification01Icon } from "@hugeicons/core-free-icons"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { NotificationPanel } from "@/components/notifications/NotificationPanel"
import { useNotificationsContext } from "@/hooks/useNotificationsContext"
import { cn } from "@/lib/utils"
import type { NotificationResponse } from "@/types/notification"

interface NotificationBellProps {
  // collapsed sidebar: a dot instead of the count
  compact?: boolean
  className?: string
}

function bellLabel(count: number | null): string {
  if (!count) return "Notificações"
  return `Notificações, ${count} ${count === 1 ? "não lida" : "não lidas"}`
}

// Bell with the unread count in the sidebar (md and up), opening the panel in a Popover. Below md the "Avisos" tab
// and /notificacoes take its place.
export function NotificationBell({ compact = false, className }: NotificationBellProps) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const { unreadCount, refresh, markRead } = useNotificationsContext()

  function handleOpenChange(next: boolean) {
    setOpen(next)
    if (next) refresh()
  }

  function openPreferences() {
    setOpen(false)
    navigate("/settings#notificacoes")
  }

  function handleSelect(notification: NotificationResponse, href: string | null) {
    markRead(notification)
    if (!href) return
    setOpen(false)
    navigate(href)
  }

  const trigger = (
    <Button
      variant="ghost"
      size="icon"
      aria-label={bellLabel(unreadCount)}
      className={cn("relative shrink-0 text-muted-foreground hover:text-foreground", className)}
    >
      <HugeiconsIcon icon={Notification01Icon} />
      {!!unreadCount &&
        (compact ? (
          <span className="absolute right-1 top-1 size-2 rounded-full bg-destructive" aria-hidden />
        ) : (
          <Badge
            variant="destructive"
            aria-hidden
            className="absolute -right-1 -top-1 h-[18px] min-w-[18px] justify-center px-1 py-0 text-[10px] leading-none"
          >
            {unreadCount > 9 ? "9+" : unreadCount}
          </Badge>
        ))}
    </Button>
  )

  const title = <span className="text-[15px] font-semibold">Notificações</span>

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>{trigger}</PopoverTrigger>
      <PopoverContent
        side="bottom"
        align="start"
        aria-label="Notificações"
        className="flex max-h-[min(560px,calc(100vh-96px))] w-[380px] max-w-[calc(100vw-32px)] flex-col overflow-hidden rounded-tile border-input bg-card p-0 shadow-panel"
      >
        <NotificationPanel
          title={title}
          onSelect={handleSelect}
          onOpenPreferences={openPreferences}
          className="flex min-h-0 flex-1 flex-col"
        />
      </PopoverContent>
    </Popover>
  )
}
