import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"
import { getChatHistory } from "@/api/chat"
import { useRealtime } from "@/hooks/useRealtime"
import type { MessageDTO } from "@/types/chat"

export const MAX_MESSAGE_LENGTH = 500

interface History {
  peladaId: number
  messages: MessageDTO[]
}

// Chat of one pelada: last 50 messages over REST, then live messages over STOMP (/topic/pelada/{id}) through the
// user's shared connection (RealtimeProvider, which owns the token and the reconnects). The server only accepts
// the subscription for members; send errors come back on /user/queue/errors.
export function usePeladaChat(peladaId: number) {
  const [history, setHistory] = useState<History | null>(null)
  const [live, setLive] = useState<History>({ peladaId, messages: [] })
  const { connectionState, subscribe, publish } = useRealtime()

  useEffect(() => {
    let cancelled = false
    getChatHistory(peladaId)
      .then((messages) => !cancelled && setHistory({ peladaId, messages }))
      .catch(() => {
        if (cancelled) return
        setHistory({ peladaId, messages: [] })
        toast.error("Não foi possível carregar as mensagens")
      })
    return () => {
      cancelled = true
    }
  }, [peladaId])

  useEffect(() => {
    const unsubscribeTopic = subscribe(`/topic/pelada/${peladaId}`, (body) => {
      try {
        const message: MessageDTO = JSON.parse(body)
        setLive((prev) =>
          prev.peladaId === peladaId
            ? { peladaId, messages: [...prev.messages, message] }
            : { peladaId, messages: [message] },
        )
      } catch {
        // ignore malformed frames
      }
    })
    const unsubscribeErrors = subscribe("/user/queue/errors", (body) => {
      try {
        const error: { message?: string } = JSON.parse(body)
        toast.error(error.message ?? "Não foi possível enviar a mensagem")
      } catch {
        toast.error("Não foi possível enviar a mensagem")
      }
    })
    return () => {
      unsubscribeTopic()
      unsubscribeErrors()
    }
  }, [peladaId, subscribe])

  const send = useCallback(
    (content: string): boolean => {
      if (connectionState !== "connected") {
        toast.error("Sem conexão com o chat")
        return false
      }
      if (!publish(`/app/pelada/${peladaId}/send`, JSON.stringify({ content }))) {
        toast.error("Não foi possível enviar a mensagem")
        return false
      }
      return true
    },
    [peladaId, connectionState, publish],
  )

  const historyMessages = history?.peladaId === peladaId ? history.messages : null
  const liveMessages = live.peladaId === peladaId ? live.messages : []
  // A message can arrive live and also be in the history response; keep one copy
  const seen = new Set(historyMessages?.map((m) => m.id) ?? [])
  const messages = [...(historyMessages ?? []), ...liveMessages.filter((m) => !seen.has(m.id))]

  return { messages, loading: historyMessages === null, connectionState, send }
}
