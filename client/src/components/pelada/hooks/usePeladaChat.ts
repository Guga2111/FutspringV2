import { useCallback, useEffect, useRef, useState } from "react"
import { Client } from "@stomp/stompjs"
import SockJS from "sockjs-client"
import { toast } from "sonner"
import { getChatHistory } from "@/api/chat"
import type { MessageDTO } from "@/types/chat"

export type ConnectionState = "connected" | "reconnecting" | "failed"
export const MAX_MESSAGE_LENGTH = 500

interface History {
  peladaId: number
  messages: MessageDTO[]
}

// Chat of one pelada: last 50 messages over REST, then live messages over STOMP (/topic/pelada/{id}).
// The server only accepts the connection with a valid token and the subscription for members; send errors
// come back on /user/queue/errors. Reconnects with exponential backoff (1 s, 2 s, 4 s), then gives up.
export function usePeladaChat(peladaId: number, token: string | null) {
  const [history, setHistory] = useState<History | null>(null)
  const [live, setLive] = useState<History>({ peladaId, messages: [] })
  const [connectionState, setConnectionState] = useState<ConnectionState>("reconnecting")
  const clientRef = useRef<Client | null>(null)

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
    let retryCount = 0
    let deactivating = false
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null

    const scheduleReconnect = (client: Client) => {
      if (deactivating || reconnectTimer !== null) return
      if (retryCount >= 3) {
        setConnectionState("failed")
        return
      }
      const delay = 2 ** retryCount * 1000
      retryCount += 1
      setConnectionState("reconnecting")
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        if (!deactivating) client.activate()
      }, delay)
    }

    const client = new Client({
      webSocketFactory: () => new SockJS("/ws"),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 0,
      onConnect: () => {
        retryCount = 0
        setConnectionState("connected")
        client.subscribe(`/topic/pelada/${peladaId}`, (frame) => {
          try {
            const message: MessageDTO = JSON.parse(frame.body)
            setLive((prev) =>
              prev.peladaId === peladaId
                ? { peladaId, messages: [...prev.messages, message] }
                : { peladaId, messages: [message] },
            )
          } catch {
            // ignore malformed frames
          }
        })
        client.subscribe("/user/queue/errors", (frame) => {
          try {
            const error: { message?: string } = JSON.parse(frame.body)
            toast.error(error.message ?? "Não foi possível enviar a mensagem")
          } catch {
            toast.error("Não foi possível enviar a mensagem")
          }
        })
      },
      onDisconnect: () => scheduleReconnect(client),
      onWebSocketError: () => scheduleReconnect(client),
      onStompError: () => scheduleReconnect(client),
    })

    client.activate()
    clientRef.current = client

    return () => {
      deactivating = true
      if (reconnectTimer !== null) clearTimeout(reconnectTimer)
      void client.deactivate()
      clientRef.current = null
    }
  }, [peladaId, token])

  const send = useCallback(
    (content: string): boolean => {
      const client = clientRef.current
      if (!client || !client.connected) {
        toast.error("Sem conexão com o chat")
        return false
      }
      try {
        client.publish({ destination: `/app/pelada/${peladaId}/send`, body: JSON.stringify({ content }) })
        return true
      } catch {
        toast.error("Não foi possível enviar a mensagem")
        return false
      }
    },
    [peladaId],
  )

  const historyMessages = history?.peladaId === peladaId ? history.messages : null
  const liveMessages = live.peladaId === peladaId ? live.messages : []
  // A message can arrive live and also be in the history response; keep one copy
  const seen = new Set(historyMessages?.map((m) => m.id) ?? [])
  const messages = [...(historyMessages ?? []), ...liveMessages.filter((m) => !seen.has(m.id))]

  return { messages, loading: historyMessages === null, connectionState, send }
}
