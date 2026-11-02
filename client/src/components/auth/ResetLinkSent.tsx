import { MailValidation01Icon } from "@hugeicons/core-free-icons"
import { AuthHeader } from "@/components/auth/AuthHeader"
import { BackToLogin } from "@/components/auth/BackToLogin"
import { Button } from "@/components/ui/button"

interface ResetLinkSentProps {
  email: string
  onUseAnotherEmail: () => void
}

// Same text whether or not the account exists: the API never tells (no user enumeration)
export function ResetLinkSent({ email, onUseAnotherEmail }: ResetLinkSentProps) {
  return (
    <div className="flex w-full max-w-sm flex-col gap-8">
      <AuthHeader icon={MailValidation01Icon} title="Verifique seu e-mail">
        Se existir uma conta com <span className="font-medium text-foreground">{email}</span>, enviamos um link para
        redefinir a senha. Ele expira em 30 minutos.
      </AuthHeader>
      <div className="flex flex-col gap-3">
        <p className="text-center text-body text-muted-foreground">
          Não chegou? Confira a caixa de spam ou tente de novo em um minuto.
        </p>
        <Button type="button" variant="outline" size="lg" className="w-full" onClick={onUseAnotherEmail}>
          Usar outro e-mail
        </Button>
      </div>
      <BackToLogin />
    </div>
  )
}
