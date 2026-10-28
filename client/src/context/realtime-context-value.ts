import { createContext } from "react"

export type ConnectionState = "connected" | "reconnecting" | "failed"

// The user's single STOMP connection, opened by RealtimeProvider in AppLayout and shared by the chat and the bell
export interface RealtimeContextValue {
  connectionState: ConnectionState
  // goes up on every CONNECT (first one and reconnects), so a hook can catch up on what it missed
  connectionId: number
  // the handler gets the frame body; the subscription survives reconnects until the returned function is called
  subscribe: (destination: string, handler: (body: string) => void) => () => void
  // false when there is no connection or the frame couldn't be sent
  publish: (destination: string, body: string) => boolean
}

export const RealtimeContext = createContext<RealtimeContextValue | null>(null)
