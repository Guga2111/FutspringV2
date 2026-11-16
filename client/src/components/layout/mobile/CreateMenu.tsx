import { useState } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { HugeiconsIcon, type IconSvgElement } from "@hugeicons/react"
import { ArrowLeft01Icon, ArrowRight01Icon, Calendar03Icon, FootballIcon, Invoice01Icon, UserAdd01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { Drawer, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle } from "@/components/ui/drawer"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { useMobileShell } from "@/hooks/useMobileShell"
import type { PeladaResponse } from "@/types/pelada"
import { areaPath, type PeladaArea } from "@/utils/peladaAreas"
import {
  CREATE_ACTION_LABEL,
  createActions,
  peladaIdFromPath,
  type CreateAction,
  type CreateActionKind,
  type CreateIntent,
  type CreateNavState,
} from "@/utils/mobileNav"

const ICON: Record<CreateActionKind, IconSvgElement> = {
  session: Calendar03Icon,
  invite: UserAdd01Icon,
  charge: Invoice01Icon,
  pelada: FootballIcon,
}

const HINT: Record<CreateActionKind, string> = {
  session: "Agende o próximo jogo",
  invite: "Chame alguém para a pelada",
  charge: "Mensalidade, diária ou evento",
  pelada: "Comece um grupo novo",
}

// the area whose layout opens the form (it consumes the router state once)
const DESTINATION: Record<CreateIntent, PeladaArea> = {
  session: "sessions",
  invite: "members",
  charge: "finance",
}

const rowClass = "h-auto min-h-14 w-full justify-start gap-3 whitespace-normal rounded-tile px-3 py-2 text-left font-normal"

// The "+" of the bottom tabs: what the caller's roles allow them to create. Inside a pelada that qualifies it is
// preselected; with several eligible peladas a second step asks which one.
export function CreateMenu({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { peladas, openCreatePelada } = useMyPeladasContext()
  const header = useMobileShell()?.header
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [picking, setPicking] = useState<CreateAction | null>(null)
  const currentPeladaId = peladaIdFromPath(pathname) ?? header?.peladaId ?? null
  const actions = createActions(peladas, currentPeladaId)

  function handleOpenChange(next: boolean) {
    if (!next) setPicking(null)
    onOpenChange(next)
  }

  function run(kind: CreateActionKind, pelada: PeladaResponse | null) {
    handleOpenChange(false)
    if (kind === "pelada" || pelada === null) {
      openCreatePelada()
      return
    }
    const state: CreateNavState = { create: kind }
    navigate(areaPath(pelada.id, DESTINATION[kind]), { state })
  }

  function choose(action: CreateAction) {
    if (action.kind === "pelada" || action.target) run(action.kind, action.target)
    else setPicking(action)
  }

  return (
    <Drawer open={open} onOpenChange={handleOpenChange}>
      <DrawerContent className="max-h-[calc(100dvh-24px)] rounded-t-[18px] bg-card pb-[env(safe-area-inset-bottom)]">
        <DrawerHeader className="flex items-center gap-2 px-4 pb-2 pt-3 text-left">
          {picking && (
            <Button
              variant="ghost"
              size="icon"
              aria-label="Voltar"
              onClick={() => setPicking(null)}
              className="-ml-2 size-10 shrink-0 rounded-[10px] [&_svg]:size-5"
            >
              <HugeiconsIcon icon={ArrowLeft01Icon} />
            </Button>
          )}
          <div className="flex min-w-0 flex-col gap-0.5">
            <DrawerTitle className="text-title font-semibold">{picking ? "Em qual pelada?" : "Criar"}</DrawerTitle>
            <DrawerDescription className={picking ? "truncate" : "sr-only"}>
              {picking ? CREATE_ACTION_LABEL[picking.kind] : "O que você quer criar"}
            </DrawerDescription>
          </div>
        </DrawerHeader>
        <div className="flex flex-col overflow-y-auto px-2 pb-4">
          {picking
            ? picking.peladas.map((pelada) => (
                <Button key={pelada.id} variant="ghost" onClick={() => run(picking.kind, pelada)} className={rowClass}>
                  <PeladaAvatar name={pelada.name} image={pelada.image} className="size-10 rounded-[10px] text-xs" />
                  <span className="min-w-0 flex-1 truncate text-body-lg font-medium">{pelada.name}</span>
                  <HugeiconsIcon icon={ArrowRight01Icon} className="size-5 text-muted-foreground" />
                </Button>
              ))
            : actions.map((action) => (
                <Button key={action.kind} variant="ghost" onClick={() => choose(action)} className={rowClass}>
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-muted text-brand-text">
                    <HugeiconsIcon icon={ICON[action.kind]} className="size-5" />
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="text-body-lg font-medium">{CREATE_ACTION_LABEL[action.kind]}</span>
                    <span className="truncate text-body-sm text-muted-foreground">
                      {action.target && action.peladas.length > 1 ? action.target.name : HINT[action.kind]}
                    </span>
                  </span>
                </Button>
              ))}
        </div>
      </DrawerContent>
    </Drawer>
  )
}
