import { Link } from "react-router-dom"
import { PeladaInvitations } from "@/components/invitations/PeladaInvitations"
import { usePeladaWorkspace } from "@/components/pelada/hooks/usePeladaWorkspace"
import { Button } from "@/components/ui/button"
import { areaPath } from "@/utils/peladaAreas"

export default function PeladaInvitationsPage() {
  const w = usePeladaWorkspace()

  // Hidden from members in the navigation; a direct link gets this state (the API refuses anyway)
  if (!w.isAdmin) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 py-24 text-center">
        <h2 className="text-xl font-semibold">Acesso negado</h2>
        <p className="text-muted-foreground">Apenas os admins podem gerenciar os convites da pelada.</p>
        <Button variant="outline" className="mt-4" asChild>
          <Link to={areaPath(w.pelada.id, "overview")}>Voltar para a pelada</Link>
        </Button>
      </div>
    )
  }

  return <PeladaInvitations peladaId={w.pelada.id} version={w.invitationVersion} onInvite={w.openInvite} />
}
