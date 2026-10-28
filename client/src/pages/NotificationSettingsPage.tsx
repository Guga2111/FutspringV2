import { NotificationPreferencesCard } from "@/components/notification-settings/NotificationPreferencesCard"

// /settings/notifications: opened from the UserMenu and from the gear in the bell's panel
export default function NotificationSettingsPage() {
  return (
    <div className="page-enter flex flex-1 flex-col">
      <main className="mx-auto flex w-full max-w-[720px] flex-col gap-6 px-4 py-5 md:px-8 md:py-7">
        <header className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold md:text-3xl">Notificações</h1>
          <p className="text-sm text-muted-foreground md:text-base">
            O sino mostra tudo o que acontece nas suas peladas. Aqui você escolhe o que também chega por e-mail.
          </p>
        </header>
        <NotificationPreferencesCard />
      </main>
    </div>
  )
}
