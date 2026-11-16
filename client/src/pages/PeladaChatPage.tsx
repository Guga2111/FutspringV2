import { ChatPanel } from "@/components/pelada/ChatPanel"
import { usePeladaWorkspace } from "@/components/pelada/hooks/usePeladaWorkspace"

// /pelada/:id/chat: the chat as a page, like a WhatsApp conversation (the pelada layout draws the frame: below md the
// mobile header with ← and the name, no bottom tabs; the composer sits above the phone's home bar)
export default function PeladaChatPage() {
  const { pelada, currentUserId } = usePeladaWorkspace()
  return <ChatPanel peladaId={pelada.id} currentUserId={currentUserId} size="page" />
}
