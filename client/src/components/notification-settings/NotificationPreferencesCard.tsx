import { cn } from "@/lib/utils"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { FieldContent, FieldDescription, FieldTitle, FieldSeparator } from "@/components/ui/field"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { DevicePushBlock } from "@/components/notification-settings/DevicePushBlock"
import { useNotificationPreferences } from "@/components/notification-settings/hooks/useNotificationPreferences"
import { usePushSubscription } from "@/hooks/usePushSubscription"
import { NOTIFICATION_CATEGORIES, notificationPreferenceText, type NotificationChannel } from "@/types/notification"

const CHANNELS: { channel: NotificationChannel; label: string }[] = [
  { channel: "push", label: "Push" },
  { channel: "email", label: "E-mail" },
]

// Column width of a channel from md (the switches line up under the header)
const COLUMN = "md:w-16 md:justify-center"

// "Neste aparelho" (Web Push on this device), then a Push and an E-mail Switch per category, each saved as soon
// as it changes. The Push column shows the saved value (it applies to the user's other devices too) but is
// disabled until this device is active. `id`: the anchor on the account settings page
export function NotificationPreferencesCard({ id }: { id?: string }) {
  const { enabled, saving, loading, error, retry, toggle } = useNotificationPreferences()
  const push = usePushSubscription()
  const pushOff = push.status !== "active"

  return (
    <Card id={id} className="scroll-mt-20 overflow-hidden rounded-tile shadow-none md:scroll-mt-6">
      <CardHeader className="p-5 md:p-6">
        <CardTitle className="text-base font-semibold">Notificações</CardTitle>
        <CardDescription>Escolha por onde cada aviso chega. As mudanças são salvas na hora.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5 p-5 pt-0 md:p-6 md:pt-0">
        <section aria-labelledby="neste-aparelho" className="flex flex-col gap-2">
          <h3 id="neste-aparelho" className="text-overline font-semibold uppercase tracking-wide text-faint-foreground">
            Neste aparelho
          </h3>
          <DevicePushBlock push={push} />
        </section>
        {error ? (
          <Alert variant="destructive">
            <AlertDescription className="flex flex-col items-start gap-2">
              Não foi possível carregar suas preferências.
              <Button variant="outline" size="sm" onClick={retry}>
                Tentar de novo
              </Button>
            </AlertDescription>
          </Alert>
        ) : (
          <div className="flex flex-col gap-4">
            <div aria-hidden className="hidden justify-end text-caption font-medium text-muted-foreground md:flex">
              {CHANNELS.map(({ channel, label }) => (
                <span key={channel} className={cn("flex", COLUMN)}>
                  {label}
                </span>
              ))}
            </div>
            {NOTIFICATION_CATEGORIES.map((category, index) => {
              const { label, description } = notificationPreferenceText[category]
              return (
                <div key={category} className="flex flex-col gap-4">
                  {index > 0 && <FieldSeparator />}
                  <div role="group" aria-label={label} className="flex flex-col gap-3 md:flex-row md:items-center">
                    <FieldContent>
                      <FieldTitle>{label}</FieldTitle>
                      <FieldDescription>{description}</FieldDescription>
                    </FieldContent>
                    <div className="flex flex-col gap-2 md:flex-row md:gap-0">
                      {CHANNELS.map(({ channel, label: channelLabel }) => (
                        <div key={channel} className={cn("flex items-center justify-between gap-3", COLUMN)}>
                          <span aria-hidden className="text-body-sm text-muted-foreground md:hidden">
                            {channelLabel}
                          </span>
                          {loading ? (
                            <Skeleton className="h-6 w-11 shrink-0 rounded-full" />
                          ) : (
                            <Switch
                              aria-label={`${channelLabel} de ${label}`}
                              checked={enabled[category]?.[channel] ?? false}
                              disabled={!!saving[category]?.[channel] || (channel === "push" && pushOff)}
                              onCheckedChange={(next) => toggle(category, channel, next)}
                            />
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </CardContent>
      <CardFooter className="border-t bg-card-elevated px-5 py-4 md:px-6">
        <p className="text-sm text-muted-foreground">As notificações dentro do app ficam sempre ativas.</p>
      </CardFooter>
    </Card>
  )
}
