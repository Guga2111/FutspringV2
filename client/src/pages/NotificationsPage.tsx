import { useEffect, useRef } from "react"
import { useNavigate } from "react-router-dom"
import { Settings01Icon } from "@hugeicons/core-free-icons"
import { MobileHeaderActions, MobileHeaderLink } from "@/components/layout/mobile/MobileHeaderActions"
import { PageContainer } from "@/components/layout/PageContainer"
import { PageHeader } from "@/components/layout/PageHeader"
import { NotificationPanel } from "@/components/notifications/NotificationPanel"
import { useIsMobile } from "@/hooks/useIsMobile"
import { useRefresh } from "@/hooks/useMobileShell"
import { useNotificationsContext } from "@/hooks/useNotificationsContext"
import type { NotificationResponse } from "@/types/notification"

// /notificacoes: the bell's panel as a page (the "Avisos" tab of the mobile shell; from md the bell's Popover is
// the way in, but the page works at any width). The gear to the preferences sits in the mobile header.
export default function NotificationsPage() {
  const navigate = useNavigate()
  const isMobile = useIsMobile()
  const { refresh, markRead } = useNotificationsContext()
  useRefresh(refresh)

  // opening the page is opening the panel: the first page and the counter, read again (once per visit; refresh
  // changes with the filter chips)
  const opened = useRef(false)
  useEffect(() => {
    if (opened.current) return
    opened.current = true
    void refresh()
  }, [refresh])

  function handleSelect(notification: NotificationResponse, href: string | null) {
    markRead(notification)
    if (href) navigate(href)
  }

  return (
    <div className="page-enter flex flex-1 flex-col">
      <MobileHeaderActions>
        <MobileHeaderLink to="/settings#notificacoes" label="Preferências de notificação" icon={Settings01Icon} />
      </MobileHeaderActions>
      <PageContainer width="narrow">
        <PageHeader breadcrumb={[{ label: "Início", to: "/home" }, { label: "Avisos" }]} title="Avisos" />
        <NotificationPanel
          title={<span className="sr-only">Notificações</span>}
          onSelect={handleSelect}
          // below md the gear is in the header
          onOpenPreferences={isMobile ? undefined : () => navigate("/settings#notificacoes")}
          className="-mx-4 flex flex-col overflow-hidden border-y bg-card md:mx-0 md:rounded-tile md:border-x"
        />
      </PageContainer>
    </div>
  )
}
