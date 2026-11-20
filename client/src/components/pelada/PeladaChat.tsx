import { useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { BubbleChatIcon, Cancel01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ChatPanel } from "@/components/pelada/ChatPanel"

interface PeladaChatProps {
  peladaId: number
  peladaName: string
  memberCount: number
  currentUserId: number | null
}

const fabClass = "fixed bottom-4 right-4 z-40 size-[54px] rounded-full shadow-fab [&_svg]:size-[22px]"

// Floating chat button with its panel, from md. Below md the chat is a page (/pelada/:id/chat), opened from the
// icon in the mobile header, as in WhatsApp
export function PeladaChat({ peladaId, peladaName, memberCount, currentUserId }: PeladaChatProps) {
  const [open, setOpen] = useState(false)

  const closeButton = (
    <Button
      variant="ghost"
      size="icon"
      aria-label="Fechar chat"
      onClick={() => setOpen(false)}
      className="size-[34px] shrink-0 text-muted-foreground hover:bg-accent hover:text-foreground [&_svg]:size-[18px]"
    >
      <HugeiconsIcon icon={Cancel01Icon} />
    </Button>
  )

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="brand" size="icon" aria-label={open ? "Fechar chat" : "Abrir chat"} className={fabClass}>
          <HugeiconsIcon icon={BubbleChatIcon} />
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
