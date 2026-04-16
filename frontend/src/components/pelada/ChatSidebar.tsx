import { useState, useEffect, useRef } from "react";
import { toast } from "sonner";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { getChatHistory } from "@/api/chat";
import type { MessageDTO } from "@/types/chat";
import { useAuth } from "@/hooks/useAuth";
import { getFileUrl } from "@/lib/utils";
import { Skeleton } from "@/components/ui/skeleton";

function formatRelativeTime(sentAt: string): string {
  const now = Date.now();
  const sent = new Date(sentAt).getTime();
  const diffMs = now - sent;
  const diffSec = Math.floor(diffMs / 1000);
  if (diffSec < 60) return "just now";
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin} min ago`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}h ago`;
  const diffDay = Math.floor(diffHour / 24);
  return `${diffDay}d ago`;
}

export function ChatSidebar({
  peladaId,
  currentUserId,
  collapsed,
}: {
  peladaId: number;
  currentUserId: number | null;
  collapsed: boolean;
  onToggle: () => void;
}) {
  const { token } = useAuth();
  const [messages, setMessages] = useState<MessageDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [inputValue, setInputValue] = useState("");
  const [connectionState, setConnectionState] = useState<
    "connected" | "reconnecting" | "failed"
  >("reconnecting");
  const bottomRef = useRef<HTMLDivElement>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const stompClientRef = useRef<Client | null>(null);
  const retryCountRef = useRef(0);
  const isDeactivatingRef = useRef(false);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Load history on mount
  useEffect(() => {
    setLoading(true);
    getChatHistory(peladaId)
      .then(setMessages)
      .catch(() => {
        /* ignore */
      })
      .finally(() => setLoading(false));
  }, [peladaId]);

  // Scroll to bottom after initial load
  useEffect(() => {
    if (!loading) {
      bottomRef.current?.scrollIntoView();
    }
  }, [loading]);

  // STOMP WebSocket connection with exponential backoff reconnection
  useEffect(() => {
    retryCountRef.current = 0;
    isDeactivatingRef.current = false;

    const scheduleReconnect = (client: Client) => {
      if (reconnectTimerRef.current !== null) return; // already scheduled
      const attempt = retryCountRef.current;
      if (attempt >= 3) {
        setConnectionState("failed");
        return;
      }
      const delay = Math.pow(2, attempt) * 1000; // 1s, 2s, 4s
      retryCountRef.current = attempt + 1;
      setConnectionState("reconnecting");
      reconnectTimerRef.current = setTimeout(() => {
        reconnectTimerRef.current = null;
        if (!isDeactivatingRef.current) {
          client.activate();
        }
      }, delay);
    };

    const client = new Client({
      webSocketFactory: () => new SockJS("/ws"),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 0,
      onConnect: () => {
        retryCountRef.current = 0;
        setConnectionState("connected");
        client.subscribe(`/topic/pelada/${peladaId}`, (frame) => {
          try {
            const msg: MessageDTO = JSON.parse(frame.body);
            setMessages((prev) => {
              const updated = [...prev, msg];
              // Auto-scroll to bottom if within 100px of bottom
              requestAnimationFrame(() => {
                const container = scrollContainerRef.current;
                if (container) {
                  const nearBottom =
                    container.scrollTop + container.clientHeight >=
                    container.scrollHeight - 100;
                  if (nearBottom) {
                    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
                  }
                }
              });
              return updated;
            });
          } catch {
            /* ignore malformed messages */
          }
        });
      },
      onDisconnect: () => {
        if (!isDeactivatingRef.current) {
          scheduleReconnect(client);
        }
      },
      onWebSocketError: () => {
        if (!isDeactivatingRef.current) {
          scheduleReconnect(client);
        }
      },
      onStompError: () => {
        if (!isDeactivatingRef.current) {
          scheduleReconnect(client);
        }
      },
    });

    client.activate();
    stompClientRef.current = client;

    return () => {
      isDeactivatingRef.current = true;
      if (reconnectTimerRef.current !== null) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      client.deactivate();
      stompClientRef.current = null;
    };
  }, [peladaId, token]);

  const sendMessage = () => {
    const content = inputValue.trim();
    if (!content) return;
    const client = stompClientRef.current;
    if (!client || !client.connected) {
      toast.error("Not connected to chat");
      return;
    }
    try {
      client.publish({
        destination: `/app/pelada/${peladaId}/send`,
        body: JSON.stringify({ content }),
      });
      setInputValue("");
    } catch {
      toast.error("Failed to send message");
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
    <div className="flex flex-col border border-border rounded-lg overflow-hidden h-full min-h-[400px] bg-background">

      {/* Connection state banner */}
      {connectionState === "reconnecting" && (
        <div className="px-3 py-1 text-xs text-center bg-yellow-100 text-yellow-800 dark:bg-yellow-900/40 dark:text-yellow-300 flex-shrink-0">
          Reconectando...
        </div>
      )}
      {connectionState === "failed" && (
        <div className="px-3 py-1 text-xs text-center bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300 flex-shrink-0">
          Conexão falhou. Atualize a página para testar.
        </div>
      )}

      {/* Body */}
      {!collapsed && (
        <>
          <div
            ref={scrollContainerRef}
            className="flex-1 overflow-y-auto p-3 space-y-3 min-h-0"
          >
            {loading ? (
              <>
                {[0, 1, 2, 3, 4].map((i) => (
                  <div key={i} className="flex items-start gap-2">
                    <Skeleton className="h-7 w-7 rounded-full flex-shrink-0" />
                    <div className="flex-1 space-y-1">
                      <Skeleton className="h-3 w-20" />
                      <Skeleton className="h-4 w-40" />
                    </div>
                  </div>
                ))}
              </>
            ) : messages.length === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-8">
                Nenhuma mensagem.
              </p>
            ) : (
              messages.map((msg) => {
                const isOwn = msg.sender.id === currentUserId;
                return (
                  <div
                    key={msg.id}
                    className={`flex items-end gap-2 ${isOwn ? "flex-row-reverse" : "flex-row"}`}
                  >
                    {/* Avatar */}
                    {!isOwn && (
                      <div className="flex-shrink-0">
                        {msg.sender.image ? (
                          <img
                            src={getFileUrl(msg.sender.image)}
                            alt={msg.sender.username}
                            className="h-7 w-7 rounded-full object-cover"
                          />
                        ) : (
                          <div className="h-7 w-7 rounded-full bg-muted flex items-center justify-center text-xs font-semibold">
                            {msg.sender.username.slice(0, 2).toUpperCase()}
                          </div>
                        )}
                      </div>
                    )}
                    <div
                      className={`flex flex-col max-w-[75%] ${isOwn ? "items-end" : "items-start"}`}
                    >
                      {!isOwn && (
                        <span className="text-xs text-muted-foreground mb-0.5">
                          {msg.sender.username}
                        </span>
                      )}
                      <div
                        className={`px-3 py-1.5 rounded-2xl text-sm break-words ${
                          isOwn
                            ? "bg-green-600 text-white rounded-br-sm"
                            : "bg-muted text-foreground rounded-bl-sm"
                        }`}
                      >
                        {msg.content}
                      </div>
                      <span
                        className="text-xs text-muted-foreground mt-0.5"
                        title={new Date(msg.sentAt).toLocaleString()}
                      >
                        {formatRelativeTime(msg.sentAt)}
                      </span>
                    </div>
                  </div>
                );
              })
            )}
            <div ref={bottomRef} />
          </div>

          {/* Input */}
          <div className="flex items-center gap-2 px-3 py-2 border-t border-border bg-background flex-shrink-0">
            <input
              type="text"
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Type a message..."
              className="flex-1 text-sm bg-muted rounded-full px-3 py-1.5 outline-none focus:ring-1 focus:ring-ring"
            />
            <button
              aria-label="Send message"
              onClick={sendMessage}
              disabled={!inputValue.trim()}
              className="text-sm font-medium text-primary disabled:opacity-40 hover:opacity-80 transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded"
            >
              Send
            </button>
          </div>
        </>
      )}
    </div>
  );
}
