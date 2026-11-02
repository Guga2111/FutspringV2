import { HugeiconsIcon } from "@hugeicons/react"
import { Link, useSearchParams } from "react-router-dom"
import { MailRemove01Icon, MailValidation01Icon } from "@hugeicons/core-free-icons"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { useEmailUnsubscribe } from "@/components/notification-settings/hooks/useEmailUnsubscribe"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { notificationCategoryLabel } from "@/types/notification"

// Opened from the "Desativar" link in a notification e-mail: /notifications/unsubscribe?token=…. Public: the
// token is the authorization, so it works logged out
export default function NotificationUnsubscribePage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token")

  return <AuthLayout>{token ? <UnsubscribeCard key={token} token={token} /> : <MissingTokenCard />}</AuthLayout>
}

function ManagePreferencesButton({ variant }: { variant: "brand" | "ghost" }) {
  return (
    <Button asChild variant={variant} className="w-full">
      <Link to="/settings/notifications">Gerenciar preferências</Link>
    </Button>
  )
}

function UnsubscribeCard({ token }: { token: string }) {
  const { preference, loading, error, pending, resubscribe, unsubscribe } = useEmailUnsubscribe(token)

  if (loading) {
    return (
      <Card className="w-full max-w-md border-0 shadow-none" aria-busy>
        <CardHeader className="items-center gap-3 text-center">
          <Skeleton className="size-10 rounded-full" />
          <Skeleton className="h-6 w-64" />
          <Skeleton className="h-4 w-72" />
        </CardHeader>
      </Card>
    )
  }

  if (error || !preference) {
    return (
      <Card className="w-full max-w-md border-0 shadow-none">
        <CardContent className="p-6">
          <Alert variant="destructive">
            <AlertTitle>Link inválido</AlertTitle>
            <AlertDescription>
              {error && error !== "Link inválido" ? error : "Abra o link exatamente como chegou no e-mail."} Você
              também pode escolher seus e-mails nas preferências.
            </AlertDescription>
          </Alert>
        </CardContent>
        <CardFooter>
          <ManagePreferencesButton variant="brand" />
        </CardFooter>
      </Card>
    )
  }

  const label = notificationCategoryLabel[preference.category]
  const Icon = preference.emailEnabled ? MailValidation01Icon : MailRemove01Icon

  return (
    <Card className="w-full max-w-md border-0 shadow-none">
      <CardHeader className="items-center text-center">
        <HugeiconsIcon icon={Icon} aria-hidden className="mb-2 size-10 text-brand-text" />
        <CardTitle aria-live="polite">
          E-mails de <span className="text-brand-text">{label}</span> {preference.emailEnabled ? "reativados" : "desativados"}
        </CardTitle>
        <CardDescription>
          {preference.emailEnabled
            ? "Você volta a receber esses e-mails."
            : "Você não vai mais receber esses e-mails. As notificações dentro do app continuam."}
        </CardDescription>
      </CardHeader>
      <CardFooter className="flex flex-col gap-2">
        {preference.emailEnabled ? (
          <Button variant="outline" className="w-full" disabled={pending} onClick={unsubscribe}>
            Desativar de novo
          </Button>
        ) : (
          <Button variant="brand" className="w-full" disabled={pending} onClick={resubscribe}>
            Reativar
          </Button>
        )}
        <ManagePreferencesButton variant="ghost" />
      </CardFooter>
    </Card>
  )
}

function MissingTokenCard() {
  return (
    <Card className="w-full max-w-md border-0 shadow-none">
      <CardHeader className="text-center">
        <CardTitle>Link incompleto</CardTitle>
        <CardDescription>
          Abra o link exatamente como chegou no e-mail, ou escolha seus e-mails nas preferências.
        </CardDescription>
      </CardHeader>
      <CardFooter>
        <ManagePreferencesButton variant="brand" />
      </CardFooter>
    </Card>
  )
}
