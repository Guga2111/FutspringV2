import { useState } from "react"
import { Link, useLocation, useNavigate, useParams } from "react-router-dom"
import { toast } from "sonner"
import { joinInviteLink } from "@/api/invitations"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { InvitationPreview } from "@/components/invitations/InvitationPreview"
import { useInvitePreview } from "@/components/invitations/hooks/useInvitePreview"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/hooks/useAuth"
import { getErrorMessage, getErrorStatus } from "@/lib/errors"

// Public preview of a share link. It never joins by itself: a logged-in visitor confirms with "Entrar na pelada".
export default function JoinPeladaPage() {
  const { token = "" } = useParams()
  const { token: session } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const { preview, loading, error, memberPeladaId, retry } = useInvitePreview(token, session)
  const [joining, setJoining] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const here = encodeURIComponent(location.pathname + location.search + location.hash)

  async function join() {
    setJoining(true)
    setActionError(null)
    try {
      const joined = await joinInviteLink(token)
      toast.success("Você entrou na pelada")
      navigate(`/pelada/${joined.peladaId}`, { replace: true })
    } catch (err) {
      setActionError(getErrorMessage(err, "Não foi possível entrar. Confira sua conexão e tente novamente."))
    } finally {
      setJoining(false)
    }
  }

  return (
    <AuthLayout>
      <div className="flex w-full max-w-sm flex-col gap-6">
        <header className="flex flex-col gap-2">
          <h1 className="font-display text-display font-bold leading-none md:text-display-lg">Bora jogar?</h1>
          <p className="text-body-lg text-muted-foreground">
            {memberPeladaId ? "Você já faz parte desta pelada." : "Você recebeu um link para entrar nesta pelada."}
          </p>
        </header>

        {loading ? (
          <Skeleton className="h-24 rounded-tile" aria-label="Carregando convite" />
        ) : error || !preview ? (
          <Alert variant="destructive">
            <AlertTitle>{getErrorStatus(error) === 410 ? "Link expirado ou revogado" : "Convite indisponível"}</AlertTitle>
            <AlertDescription className="flex flex-col items-start gap-3">
              {getErrorStatus(error) === 410
                ? "Peça um link novo a um admin da pelada."
                : getErrorMessage(error, "Não foi possível carregar o convite. Confira sua conexão.")}
              {getErrorStatus(error) !== 410 && <Button variant="outline" size="sm" onClick={retry}>Tentar novamente</Button>}
            </AlertDescription>
          </Alert>
        ) : (
          <>
            <div className="rounded-tile bg-card p-4 md:p-5">
              <InvitationPreview pelada={preview} size="lg" />
            </div>
            {actionError && (
              <Alert variant="destructive">
                <AlertDescription>{actionError}</AlertDescription>
              </Alert>
            )}
            {memberPeladaId ? (
              <Button asChild variant="brand" size="lg" className="w-full">
                <Link to={`/pelada/${memberPeladaId}`}>Abrir pelada</Link>
              </Button>
            ) : session ? (
              <Button variant="brand" size="lg" className="w-full" disabled={joining} onClick={join}>
                {joining ? "Entrando…" : "Entrar na pelada"}
              </Button>
            ) : (
              <div className="flex flex-col gap-3">
                <p className="text-body text-muted-foreground">Entre ou crie sua conta para confirmar a entrada.</p>
                <Button asChild variant="brand" size="lg" className="w-full">
                  <Link to={`/auth?tab=login&returnTo=${here}`}>Entrar</Link>
                </Button>
                <Button asChild variant="outline" size="lg" className="w-full">
                  <Link to={`/auth?tab=register&returnTo=${here}`}>Criar conta</Link>
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </AuthLayout>
  )
}
