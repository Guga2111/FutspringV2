import * as React from "react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer"
import { useIsMobile } from "@/hooks/useIsMobile"
import { cn } from "@/lib/utils"

// A Dialog from md and a bottom sheet (Drawer) below it, with one API: what phones' apps do for a short form.
// Confirmations stay on AlertDialog (centred, as the system's alerts); side forms stay on Sheet.
const ResponsiveDialogContext = React.createContext(false)

function ResponsiveDialog({
  open,
  onOpenChange,
  children,
}: {
  open?: boolean
  onOpenChange?: (open: boolean) => void
  children: React.ReactNode
}) {
  const isMobile = useIsMobile()
  const Root = isMobile ? Drawer : Dialog
  return (
    <ResponsiveDialogContext.Provider value={isMobile}>
      <Root open={open} onOpenChange={onOpenChange}>
        {children}
      </Root>
    </ResponsiveDialogContext.Provider>
  )
}

interface ResponsiveDialogContentProps {
  // the Dialog's classes (width, padding…); the sheet has its own sizing
  className?: string
  drawerClassName?: string
  // the content lays itself out (its own scroll area and padding): the sheet adds none
  bare?: boolean
  children: React.ReactNode
}

function ResponsiveDialogContent({ className, drawerClassName, bare = false, children }: ResponsiveDialogContentProps) {
  const drawer = React.useContext(ResponsiveDialogContext)
  if (drawer) {
    return (
      <DrawerContent className={cn("max-h-[calc(100dvh-24px)] rounded-t-[18px] bg-card", drawerClassName)}>
        {bare ? (
          children
        ) : (
          <div className="flex min-h-0 flex-col gap-4 overflow-y-auto px-4 pb-[calc(1rem+env(safe-area-inset-bottom))] pt-3">
            {children}
          </div>
        )}
      </DrawerContent>
    )
  }
  return <DialogContent className={className}>{children}</DialogContent>
}

function ResponsiveDialogHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  const drawer = React.useContext(ResponsiveDialogContext)
  return drawer ? <DrawerHeader className={cn("p-0 text-left", className)} {...props} /> : <DialogHeader className={className} {...props} />
}

function ResponsiveDialogFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  const drawer = React.useContext(ResponsiveDialogContext)
  // in the sheet the main action (the last button) goes on top, full width, under the thumb
  return drawer ? (
    <DrawerFooter className={cn("mt-0 flex-col-reverse p-0", className)} {...props} />
  ) : (
    <DialogFooter className={className} {...props} />
  )
}

const ResponsiveDialogTitle = React.forwardRef<HTMLHeadingElement, React.HTMLAttributes<HTMLHeadingElement>>(
  ({ className, ...props }, ref) => {
    const drawer = React.useContext(ResponsiveDialogContext)
    return drawer ? (
      <DrawerTitle ref={ref} className={cn("text-title", className)} {...props} />
    ) : (
      <DialogTitle ref={ref} className={className} {...props} />
    )
  },
)
ResponsiveDialogTitle.displayName = "ResponsiveDialogTitle"

const ResponsiveDialogDescription = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLParagraphElement>>(
  ({ className, ...props }, ref) => {
    const drawer = React.useContext(ResponsiveDialogContext)
    return drawer ? (
      <DrawerDescription ref={ref} className={className} {...props} />
    ) : (
      <DialogDescription ref={ref} className={className} {...props} />
    )
  },
)
ResponsiveDialogDescription.displayName = "ResponsiveDialogDescription"

export {
  ResponsiveDialog,
  ResponsiveDialogContent,
  ResponsiveDialogDescription,
  ResponsiveDialogFooter,
  ResponsiveDialogHeader,
  ResponsiveDialogTitle,
}
