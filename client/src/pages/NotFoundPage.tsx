import { Link } from "react-router-dom"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/hooks/useAuth"

export default function NotFoundPage() {
  const { token } = useAuth()
  return (
    <AuthLayout>
      <div className="flex w-full max-w-sm flex-col gap-6">
        <p className="font-display text-hero font-extrabold leading-none text-brand-text tabular-nums" aria-hidden>
          404
        </p>
        <div className="flex flex-col gap-2">
          <h1 className="font-display text-display font-bold leading-none md:text-display-lg">Bola fora</h1>
          <p className="text-body-lg text-muted-foreground">
            Essa página saiu pela linha de fundo. O link pode estar errado ou a página não existe mais.
          </p>
        </div>
        <Button asChild variant="brand" size="lg" className="w-full md:w-auto md:self-start">
          <Link to={token ? "/home" : "/"}>{token ? "Voltar para o início" : "Ir para a página inicial"}</Link>
        </Button>
      </div>
    </AuthLayout>
  )
}
