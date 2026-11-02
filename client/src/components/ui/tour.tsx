// Guided tour, from the shadcn.io Ark registry (`ark/tour`), adapted: Radix/shadcn have no tour, so this is the
// one primitive built on Ark UI (@ark-ui/react). Only tour.tsx was taken: the registry's Ark button/dialog would
// overwrite ours, so it uses our Button and Dialog pieces, Hugeicons, Tailwind 3 and the semantic tokens.
import * as React from "react"
import { Portal } from "@ark-ui/react/portal"
import { Tour as ArkTour, type UseTourProps, useTour } from "@ark-ui/react/tour"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowLeft01Icon, ArrowRight01Icon, Cancel01Icon } from "@hugeicons/core-free-icons"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { DialogFooter, DialogHeader } from "@/components/ui/dialog"
import { TourContext, useTourContext } from "@/components/ui/tour-context"


// The machine options (steps, onStatusChange, translations…) go to useTour; the rest to Tour.Root
type TourProps = UseTourProps & Omit<React.ComponentProps<typeof ArkTour.Root>, "tour">

export function Tour({ steps = [], onStatusChange, onStepChange, keyboardNavigation, closeOnEscape, closeOnInteractOutside, translations, spotlightOffset, spotlightRadius, lazyMount = true, unmountOnExit = true, ...rest }: TourProps) {
  const tour = useTour({ steps, onStatusChange, onStepChange, keyboardNavigation, closeOnEscape, closeOnInteractOutside, translations, spotlightOffset, spotlightRadius })
  return (
    <TourContext.Provider value={tour}>
      <ArkTour.Root data-slot="tour" tour={tour} lazyMount={lazyMount} unmountOnExit={unmountOnExit} {...rest} />
    </TourContext.Provider>
  )
}

export function TourContent({ showCloseButton = true, className, children, ...rest }: React.ComponentProps<typeof ArkTour.Content> & { showCloseButton?: boolean }) {
  return (
    <Portal>
      <ArkTour.Backdrop data-slot="tour-overlay" className="fixed inset-0 z-50 bg-overlay/60 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
      <ArkTour.Spotlight data-slot="tour-spotlight" className="z-50 rounded-tile border-2 border-brand" />
      <ArkTour.Positioner
        data-slot="tour-positioner"
        className={cn(
          "z-50 [--tour-z-index:50]",
          "data-[type=dialog]:fixed data-[type=dialog]:inset-0 data-[type=dialog]:flex data-[type=dialog]:items-center data-[type=dialog]:justify-center",
          "data-[type=floating]:absolute data-[type=tooltip]:absolute",
        )}
      >
        <ArkTour.Content
          data-slot="tour-content"
          className={cn(
            "relative flex w-[min(calc(100vw-2rem),24rem)] flex-col gap-4 rounded-hero border bg-card p-5 text-card-foreground shadow-dialog",
            "focus:outline-none",
            "data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95",
            "motion-reduce:!animate-none",
            className,
          )}
          {...rest}
        >
          {children}
          {showCloseButton && (
            <ArkTour.CloseTrigger asChild>
              <Button variant="ghost" size="icon" className="absolute right-3 top-3 size-8" aria-label="Fechar tour">
                <HugeiconsIcon icon={Cancel01Icon} className="size-4" />
              </Button>
            </ArkTour.CloseTrigger>
          )}
        </ArkTour.Content>
      </ArkTour.Positioner>
    </Portal>
  )
}

export function TourHeader({ className, ...props }: React.ComponentProps<"div">) {
  return <DialogHeader data-slot="tour-header" className={cn("pr-8 text-left", className)} {...props} />
}

export function TourTitle({ className, ...rest }: React.ComponentProps<typeof ArkTour.Title>) {
  const tour = useTourContext()
  return <ArkTour.Title data-slot="tour-title" className={cn("text-title font-semibold leading-tight", className)} {...rest}>{tour.step?.title}</ArkTour.Title>
}

export function TourDescription({ className, ...rest }: React.ComponentProps<typeof ArkTour.Description>) {
  const tour = useTourContext()
  return <ArkTour.Description data-slot="tour-description" className={cn("text-body text-muted-foreground", className)} {...rest}>{tour.step?.description}</ArkTour.Description>
}

export function TourProgressText({ className, ...rest }: React.ComponentProps<typeof ArkTour.ProgressText>) {
  const tour = useTourContext()
  return <ArkTour.ProgressText data-slot="tour-progress-text" className={cn("text-caption text-muted-foreground", className)} {...rest}>{tour.getProgressText()}</ArkTour.ProgressText>
}

// The current step's actions: "next" is the brand action ("next" on the last step completes the tour),
// "prev"/"dismiss"/"skip" are outline
export function TourActions({ className, ...rest }: React.ComponentProps<"div">) {
  const tour = useTourContext()
  const actions = tour.step?.actions ?? []
  if (actions.length === 0) return null
  return (
    <ArkTour.Control asChild>
      <DialogFooter data-slot="tour-actions" className={cn("flex-row flex-wrap justify-end gap-2 sm:space-x-0", className)} {...rest}>
        {actions.map((action) => (
          <ArkTour.ActionTrigger key={action.label} action={action} asChild>
            <Button size="sm" variant={action.action === "next" ? "brand" : "outline"}>
              {action.action === "prev" && <HugeiconsIcon icon={ArrowLeft01Icon} className="size-4" />}
              {action.label}
              {action.action === "next" && tour.hasNextStep && <HugeiconsIcon icon={ArrowRight01Icon} className="size-4" />}
            </Button>
          </ArkTour.ActionTrigger>
        ))}
      </DialogFooter>
    </ArkTour.Control>
  )
}
