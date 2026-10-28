import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldContent, FieldDescription, FieldGroup, FieldLabel, FieldSeparator } from "@/components/ui/field"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { useNotificationPreferences } from "@/components/notification-settings/hooks/useNotificationPreferences"
import { NOTIFICATION_CATEGORIES, notificationPreferenceText } from "@/types/notification"

// One Switch per category, saved as soon as it changes
export function NotificationPreferencesCard() {
  const { emailEnabled, saving, loading, error, retry, toggle } = useNotificationPreferences()

  return (
    <Card className="overflow-hidden rounded-tile shadow-none">
      <CardHeader className="p-5 md:p-6">
        <CardTitle className="text-base font-semibold">E-mail</CardTitle>
        <CardDescription>Escolha o que chega no seu e-mail. As mudanças são salvas na hora.</CardDescription>
      </CardHeader>
      <CardContent className="p-5 pt-0 md:p-6 md:pt-0">
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
          <FieldGroup className="gap-4">
            {NOTIFICATION_CATEGORIES.map((category, index) => {
              const id = `email-${category.toLowerCase()}`
              const { label, description } = notificationPreferenceText[category]
              return (
                <div key={category} className="flex flex-col gap-4">
                  {index > 0 && <FieldSeparator />}
                  <Field orientation="horizontal" data-disabled={loading || !!saving[category]}>
                    <FieldContent>
                      <FieldLabel htmlFor={id}>{label}</FieldLabel>
                      <FieldDescription>{description}</FieldDescription>
                    </FieldContent>
                    {loading ? (
                      <Skeleton className="h-6 w-11 shrink-0 rounded-full" />
                    ) : (
                      <Switch
                        id={id}
                        checked={emailEnabled[category] ?? false}
                        disabled={!!saving[category]}
                        onCheckedChange={(next) => toggle(category, next)}
                      />
                    )}
                  </Field>
                </div>
              )
            })}
          </FieldGroup>
        )}
      </CardContent>
      <CardFooter className="border-t bg-card-elevated px-5 py-4 md:px-6">
        <p className="text-sm text-muted-foreground">As notificações dentro do app ficam sempre ativas.</p>
      </CardFooter>
    </Card>
  )
}
