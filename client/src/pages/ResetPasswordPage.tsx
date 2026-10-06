import { Link, useSearchParams } from "react-router-dom"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { ResetPasswordForm } from "@/components/auth/ResetPasswordForm"
import { Button } from "@/components/ui/button"
import { Card, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"

// Opened from the e-mail link: /reset-password?token=…
export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token")

  return <AuthLayout>{token ? <ResetPasswordForm token={token} /> : <MissingTokenCard />}</AuthLayout>
}

function MissingTokenCard() {
  return (
    <Card className="w-full max-w-md border-0 shadow-none">
      <CardHeader className="text-center">
        <CardTitle>Link incompleto</CardTitle>
        <CardDescription>
          Abra o link exatamente como chegou no e-mail ou peça um novo para redefinir a senha.
        </CardDescription>
      </CardHeader>
      <CardFooter className="flex flex-col gap-2">
        <Button asChild variant="gradient" className="w-full">
          <Link to="/forgot-password">Pedir um novo link</Link>
        </Button>
        <Button asChild variant="ghost" className="w-full">
          <Link to="/auth?tab=login">Voltar para o login</Link>
        </Button>
      </CardFooter>
    </Card>
  )
}
