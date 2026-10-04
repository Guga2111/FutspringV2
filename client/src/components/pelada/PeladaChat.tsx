import { useState } from "react"
import { MessageCircle, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "@/components/ui/drawer"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ChatPanel } from "@/components/pelada/ChatPanel"
import { useIsMobile } from "@/hooks/useIsMobile"

interface PeladaChatProps {
  peladaId: number
  peladaName: string
  memberCount: number
  currentUserId: number | null
}

const fabClass = "fixed bottom-4 right-4 z-40 size-[54px] rounded-full shadow-fab [&_svg]:size-[22px]"

// Floating chat button: a panel above it on desktop, a bottom drawer on mobile
export function PeladaChat({ peladaId, peladaName, memberCount, currentUserId }: PeladaChatProps) {
  const isMobile = useIsMobile()
  const [open, setOpen] = useState(false)

  const closeButton = (
    <Button
      variant="ghost"
      size="icon"
      aria-label="Fechar chat"
      onClick={() => setOpen(false)}
      className="size-[34px] shrink-0 text-muted-foreground hover:bg-accent hover:text-foreground [&_svg]:size-[18px]"
    >
      <X />
    </Button>
  )

  if (isMobile) {
    return (
      <>
        {!open && (
          <Button variant="gradient" size="icon" aria-label="Abrir chat" onClick={() => setOpen(true)} className={fabClass}>
            <MessageCircle />
          </Button>
        )}
        <Drawer open={open} onOpenChange={setOpen}>
          <DrawerContent className="h-[78%] rounded-t-2xl bg-card [&>div:first-child]:mt-2 [&>div:first-child]:h-1 [&>div:first-child]:w-10 [&>div:first-child]:bg-star-off">
            <div className="flex items-center justify-between border-b py-2 pl-4 pr-2">
              <DrawerTitle className="text-[15px] font-semibold">Chat</DrawerTitle>
              <DrawerDescription className="sr-only">
                {peladaName} · {memberCount} membros
              </DrawerDescription>
              {closeButton}
            </div>
            <ChatPanel peladaId={peladaId} currentUserId={currentUserId} size="drawer" />
          </DrawerContent>
        </Drawer>
      </>
    )
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="gradient" size="icon" aria-label={open ? "Fechar chat" : "Abrir chat"} className={fabClass}>
          <MessageCircle />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        side="top"
        align="end"
        sideOffset={16}
        alignOffset={8}
        aria-label="Chat da pelada"
        className="flex h-[calc(100vh-158px)] w-[360px] max-w-[calc(100vw-48px)] flex-col overflow-hidden rounded-tile border-input bg-card p-0 shadow-panel"
      >
        <div className="flex items-center justify-between border-b py-2.5 pl-4 pr-2">
          <div className="flex flex-col">
            <span className="text-[15px] font-semibold">Chat</span>
            <span className="text-xs text-subtle-foreground">
              {peladaName} · {memberCount} membros
            </span>
          </div>
          {closeButton}
        </div>
        <ChatPanel peladaId={peladaId} currentUserId={currentUserId} />
      </PopoverContent>
    </Popover>
  )
}
