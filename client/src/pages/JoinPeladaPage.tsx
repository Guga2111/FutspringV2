import { useEffect, useState } from "react"
import { Link, useLocation, useNavigate, useParams } from "react-router-dom"
import { toast } from "sonner"
import { getInvitePreview, joinInviteLink } from "@/api/invitations"
import { getMyPeladas } from "@/api/peladas"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { InvitationPreview } from "@/components/invitations/InvitationPreview"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/hooks/useAuth"
import { getErrorMessage } from "@/lib/errors"
import type { PeladaPreview } from "@/types/invitation"

export default function JoinPeladaPage() {
  const { token = "" } = useParams()
  const { token: session } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [version, setVersion] = useState(0)
  const [result, setResult] = useState<{ token: string; preview?: PeladaPreview; error?: unknown } | null>(null)
  const [member, setMember] = useState<{ token: string; session: string; id: number } | null>(null)
  const [pending, setPending] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  useEffect(() => {
    let active = true
    getInvitePreview(token).then(preview => { if (active) setResult({ token, preview }) })
      .catch(error => { if (active) setResult({ token, error }) })
    return () => { active = false }
  }, [token, version])
  const current = result?.token === token ? result : null
  const preview = current?.preview
  useEffect(() => {
    if (!session || !preview) return
    let active = true
    getMyPeladas().then(peladas => {
      if (active && peladas.some(p => p.id === preview.id)) setMember({ token, session, id: preview.id })
    }).catch(() => { /* Joining will still check membership and authorization on the server. */ })
    return () => { active = false }
  }, [session, preview, token])
  async function join() {
    setPending(true)
    setActionError(null)
    try {
      const joined = await joinInviteLink(token)
      toast.success("Você entrou na pelada")
      navigate(`/pelada/${joined.peladaId}`, { replace: true })
    } catch (error) { setActionError(getErrorMessage(error, "Não foi possível entrar. Confira sua conexão e tente novamente.")) }
    finally { setPending(false) }
  }
  const memberId = member?.token === token && member.session === session ? member.id : null
  return (
    <AuthLayout>
      <Card className="w-full max-w-md">
        <CardHeader><h1 className="text-2xl font-semibold tracking-tight">Convite para participar</h1></CardHeader>
        <CardContent className="flex flex-col gap-5">
          {!current ? <Skeleton className="h-28" /> : current.error ? <Alert variant="destructive"><AlertDescription>{getErrorMessage(current.error, "Não foi possível carregar o convite. Confira sua conexão.")}</AlertDescription></Alert> : preview && <>
            <InvitationPreview pelada={preview} />
            <p className="text-sm text-muted-foreground">{session ? "Confirme abaixo para participar da pelada." : "Entre ou crie sua conta para confirmar sua participação."}</p>
          </>}
          {actionError && <Alert variant="destructive"><AlertDescription>{actionError}</AlertDescription></Alert>}
        </CardContent>
        <CardFooter className="flex flex-wrap gap-2">
          {current?.error ? <Button variant="outline" onClick={() => setVersion(v => v + 1)}>Tentar novamente</Button> : preview && (
            memberId ? <Button asChild><Link to={`/pelada/${memberId}`}>Abrir pelada</Link></Button> : session ? <Button disabled={pending} onClick={join}>{pending ? "Entrando…" : "Entrar na pelada"}</Button> : <>
              <Button asChild><Link to={`/auth?tab=login&returnTo=${encodeURIComponent(location.pathname + location.search + location.hash)}`}>Entrar</Link></Button>
              <Button asChild variant="outline"><Link to={`/auth?tab=register&returnTo=${encodeURIComponent(location.pathname + location.search + location.hash)}`}>Criar conta</Link></Button>
            </>
          )}
        </CardFooter>
      </Card>
    </AuthLayout>
  )
}
