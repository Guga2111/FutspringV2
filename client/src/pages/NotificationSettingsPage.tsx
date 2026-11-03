import { PageContainer } from "@/components/layout/PageContainer"
import { PageHeader } from "@/components/layout/PageHeader"
import { NotificationPreferencesCard } from "@/components/notification-settings/NotificationPreferencesCard"

// /settings/notifications: opened from the UserMenu and from the gear in the bell's panel
export default function NotificationSettingsPage() {
  return (
    <div className="page-enter flex flex-1 flex-col">
      <PageContainer width="narrow">
        <PageHeader
          breadcrumb={[{ label: "Início", to: "/home" }, { label: "Notificações" }]}
          title="Notificações"
          description="O sino mostra tudo o que acontece nas suas peladas. Aqui você escolhe o que também chega por e-mail."
        />
        <NotificationPreferencesCard />
      </PageContainer>
    </div>
  )
}
