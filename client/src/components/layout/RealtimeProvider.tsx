import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react"
import { Client, ReconnectionTimeMode, type StompSubscription } from "@stomp/stompjs"
import SockJS from "sockjs-client"
import { RealtimeContext, type ConnectionState, type RealtimeContextValue } from "@/context/realtime-context-value"
import { useAuth } from "@/hooks/useAuth"

interface Subscription {
  destination: string
  handler: (body: string) => void
  // the broker subscription of the current connection; replaced on every reconnect
  stomp: StompSubscription | null
}

// The user's single STOMP connection (/ws), shared by the chat and the notifications. The server only accepts it
// with a valid token. Reconnects with exponential backoff (1 s, 2 s, 4 s), then gives up until the tab becomes
// visible again or the token changes. Subscriptions are kept here and re-made on every connect.
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { token } = useAuth()
  const [connectionState, setConnectionState] = useState<ConnectionState>("reconnecting")
  const [connectionId, setConnectionId] = useState(0)
  const clientRef = useRef<Client | null>(null)
  const subscriptionsRef = useRef(new Map<number, Subscription>())
  const nextIdRef = useRef(0)

  useEffect(() => {
    if (!token) return
    // failed attempts since the last CONNECT; the client's own reconnect waits 1 s, 2 s, 4 s between them
    let retryCount = 0
    let gaveUp = false
    let deactivating = false

    const client = new Client({
      webSocketFactory: () => new SockJS("/ws"),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 1000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      maxReconnectDelay: 4000,
      onConnect: () => {
        retryCount = 0
        setConnectionState("connected")
        setConnectionId((id) => id + 1)
        for (const subscription of subscriptionsRef.current.values()) {
          subscription.stomp = client.subscribe(subscription.destination, (frame) => subscription.handler(frame.body))
        }
      },
      // Every lost or refused connection ends here (an API restart only fires this one; a rejected CONNECT
      // closes the socket after the ERROR frame), before the client schedules its next attempt
      onWebSocketClose: () => {
        if (deactivating || gaveUp) return
        retryCount += 1
        if (retryCount > 3) {
          gaveUp = true
          setConnectionState("failed")
          void client.deactivate()
          return
        }
        setConnectionState("reconnecting")
      },
    })

    // After giving up, coming back to the tab starts over
    function onVisibilityChange() {
      if (document.visibilityState !== "visible" || !gaveUp || deactivating) return
      gaveUp = false
      retryCount = 0
      setConnectionState("reconnecting")
      client.activate()
    }

    client.activate()
    clientRef.current = client
    document.addEventListener("visibilitychange", onVisibilityChange)

    return () => {
      deactivating = true
      document.removeEventListener("visibilitychange", onVisibilityChange)
      void client.deactivate()
      clientRef.current = null
    }
  }, [token])

  const subscribe = useCallback((destination: string, handler: (body: string) => void) => {
    const id = ++nextIdRef.current
    const subscription: Subscription = { destination, handler, stomp: null }
    subscriptionsRef.current.set(id, subscription)
    const client = clientRef.current
    if (client?.connected) {
      subscription.stomp = client.subscribe(destination, (frame) => subscription.handler(frame.body))
    }
    return () => {
      subscriptionsRef.current.delete(id)
      if (subscription.stomp && clientRef.current?.connected) {
        try {
          subscription.stomp.unsubscribe()
        } catch {
          // the connection it belonged to is gone
        }
      }
    }
  }, [])

  const publish = useCallback((destination: string, body: string) => {
    const client = clientRef.current
    if (!client?.connected) return false
    try {
      client.publish({ destination, body })
      return true
    } catch {
      return false
    }
  }, [])

  const value = useMemo<RealtimeContextValue>(
    () => ({ connectionState, connectionId, subscribe, publish }),
    [connectionState, connectionId, subscribe, publish],
  )

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}
