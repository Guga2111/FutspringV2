import { MailCheck } from "lucide-react"
import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"

interface ResetLinkSentProps {
  email: string
  onUseAnotherEmail: () => void
}

// Same text whether or not the account exists: the API never tells (no user enumeration)
export function ResetLinkSent({ email, onUseAnotherEmail }: ResetLinkSentProps) {
  return (
    <Card className="w-full max-w-md border-0 shadow-none">
      <CardHeader className="items-center text-center">
        <MailCheck aria-hidden className="mb-2 size-10 text-brand" />
        <CardTitle>Verifique seu e-mail</CardTitle>
        <CardDescription>
          Se existir uma conta com <span className="font-medium text-foreground">{email}</span>, enviamos um link
          para redefinir a senha. Ele expira em 30 minutos.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <p className="text-center text-sm text-muted-foreground">
          Não chegou? Confira a caixa de spam ou tente de novo em um minuto.
        </p>
      </CardContent>
      <CardFooter className="flex flex-col gap-2">
        <Button asChild variant="gradient" className="w-full">
          <Link to="/auth?tab=login">Voltar para o login</Link>
        </Button>
        <Button type="button" variant="ghost" className="w-full" onClick={onUseAnotherEmail}>
          Usar outro e-mail
        </Button>
      </CardFooter>
    </Card>
  )
}
