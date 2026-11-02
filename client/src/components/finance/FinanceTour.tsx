import { useEffect, useMemo, useRef, type ReactNode } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { HelpCircleIcon } from "@hugeicons/core-free-icons"
import type { TourStatusChangeDetails } from "@ark-ui/react/tour"
import { Button } from "@/components/ui/button"
import { Tour, TourActions, TourContent, TourDescription, TourHeader, TourProgressText, TourTitle } from "@/components/ui/tour"
import { useTourContext } from "@/components/ui/tour-context"
import { useFinanceTour } from "@/components/finance/hooks/useFinanceTour"
import { financeTourKey, financeTourSteps } from "@/utils/financeTour"

const ENDED: ReadonlySet<TourStatusChangeDetails["status"]> = new Set(["completed", "dismissed", "skipped"])

interface FinanceTourProps {
  manage: boolean
  viewCash: boolean
  // the collections list is on screen with nothing open over it (every step's target exists)
  canAutoStart: boolean
  children: ReactNode
}

// The first-visit tour of the finance page, one per role; seen state on the server (useFinanceTour).
// spotlightRadius matches rounded-tile on the spotlight frame.
// Wraps the page so FinanceTourButton can restart it.
export function FinanceTour({ manage, viewCash, canAutoStart, children }: FinanceTourProps) {
  const tourKey = financeTourKey(manage)
  const { shouldAutoStart, markSeen } = useFinanceTour(tourKey)
  const steps = useMemo(() => financeTourSteps(manage, viewCash), [manage, viewCash])
  return (
    <Tour
      steps={steps}
      onStatusChange={({ status }) => { if (ENDED.has(status)) markSeen(tourKey) }}
      spotlightRadius={14}
      translations={{ progressText: ({ current, total }) => `${current + 1} de ${total}`, close: "Fechar tour", nextStep: "Próximo", prevStep: "Voltar", skip: "Pular" }}
    >
      <AutoStart when={shouldAutoStart && canAutoStart} />
      {children}
      <TourContent>
        <TourHeader>
          <TourProgressText />
          <TourTitle />
          <TourDescription />
        </TourHeader>
        <TourActions />
      </TourContent>
    </Tour>
  )
}

function AutoStart({ when }: { when: boolean }) {
  const tour = useTourContext()
  const started = useRef(false)
  useEffect(() => {
    if (!when || started.current) return
    started.current = true
    tour.start()
  }, [when, tour])
  return null
}

// "?" next to Atualizar: brings the collections list on screen (onPrepare), then restarts the tour
export function FinanceTourButton({ onPrepare }: { onPrepare: () => void }) {
  const tour = useTourContext()
  return (
    <Button
      variant="ghost"
      size="icon"
      data-tour="help"
      aria-label="Ver tour do financeiro"
      onClick={() => {
        onPrepare()
        // after React renders the list the steps point at
        requestAnimationFrame(() => tour.start())
      }}
    >
      <HugeiconsIcon icon={HelpCircleIcon} />
    </Button>
  )
}
