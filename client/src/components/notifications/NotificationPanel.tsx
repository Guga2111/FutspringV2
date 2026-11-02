import type { ReactNode } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Settings01Icon, TickDouble02Icon } from "@hugeicons/core-free-icons"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { NotificationItem } from "@/components/notifications/NotificationItem"
import type { NotificationFilter } from "@/context/notifications-context-value"
import { useNotificationsContext } from "@/hooks/useNotificationsContext"
import type { NotificationResponse } from "@/types/notification"

interface NotificationPanelProps {
  // the title is rendered by the Popover / Drawer around it
  title: ReactNode
  onSelect: (notification: NotificationResponse, href: string | null) => void
  // the gear: closes the panel and goes to the e-mail preferences
  onOpenPreferences: () => void
  className?: string
}

function ItemSkeleton() {
  return (
    <div className="flex gap-3 px-3 py-2.5">
      <Skeleton className="size-9 shrink-0 rounded-full" />
      <div className="flex flex-1 flex-col gap-1.5">
        <Skeleton className="h-3.5 w-full" />
        <Skeleton className="h-3.5 w-2/3" />
        <Skeleton className="h-3 w-16" />
      </div>
    </div>
  )
}

// Content of the bell's panel: header with the filter chips, the list and "Carregar mais"
export function NotificationPanel({ title, onSelect, onOpenPreferences, className }: NotificationPanelProps) {
  const { items, filter, loading, error, hasMore, loadingMore, unreadCount, setFilter, loadMore, markAllRead, refresh } =
    useNotificationsContext()

  return (
    <div className={className}>
      <div className="flex flex-col gap-2.5 border-b px-4 pb-3 pt-3">
        <div className="flex items-center justify-between gap-2">
          {title}
          <div className="flex items-center gap-0.5">
            <Button
              variant="ghost"
              size="sm"
              onClick={markAllRead}
              disabled={!unreadCount}
              className="h-8 px-2.5 text-xs text-muted-foreground"
            >
              <HugeiconsIcon icon={TickDouble02Icon} />
              Marcar todas como lidas
            </Button>
            <Button
              variant="ghost"
              size="icon"
              onClick={onOpenPreferences}
              aria-label="Preferências de notificação"
              className="size-8 text-muted-foreground"
            >
              <HugeiconsIcon icon={Settings01Icon} />
            </Button>
          </div>
        </div>
        <ToggleGroup
          type="single"
          variant="chip"
          size="chip"
          value={filter}
          onValueChange={(value) => value && setFilter(value as NotificationFilter)}
          aria-label="Filtrar notificações"
          className="justify-start gap-1.5"
        >
          <ToggleGroupItem value="all">Todas</ToggleGroupItem>
          <ToggleGroupItem value="unread">Não lidas</ToggleGroupItem>
        </ToggleGroup>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-1.5">
        {loading ? (
          Array.from({ length: 4 }, (_, i) => <ItemSkeleton key={i} />)
        ) : error ? (
          <Alert variant="destructive" className="m-1.5 w-auto">
            <AlertDescription className="flex flex-col items-start gap-2">
              Não foi possível carregar as notificações.
              <Button variant="outline" size="sm" onClick={refresh}>
                Tentar de novo
              </Button>
            </AlertDescription>
          </Alert>
        ) : items.length === 0 ? (
          <p className="px-4 py-10 text-center text-sm text-muted-foreground">
            {filter === "unread" ? "Nenhuma notificação não lida" : "Nenhuma notificação por aqui"}
          </p>
        ) : (
          <ul className="flex flex-col gap-0.5">
            {items.map((n) => (
              <li key={n.id}>
                <NotificationItem notification={n} onSelect={onSelect} />
              </li>
            ))}
          </ul>
        )}
        {!loading && !error && hasMore && (
          <div className="flex justify-center p-2">
            <Button variant="outline" size="sm" onClick={loadMore} disabled={loadingMore}>
              {loadingMore ? "Carregando…" : "Carregar mais"}
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
