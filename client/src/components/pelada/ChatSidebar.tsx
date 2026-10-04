import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { SendHorizontal } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { cn, getFileUrl, getInitials } from "@/lib/utils";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { MAX_MESSAGE_LENGTH, usePeladaChat } from "@/components/pelada/hooks/usePeladaChat";

const relativeTime = new Intl.RelativeTimeFormat("pt-BR", { numeric: "auto" });

function formatRelativeTime(sentAt: string): string {
  const diffSec = Math.floor((Date.now() - new Date(sentAt).getTime()) / 1000);
  if (diffSec < 60) return "agora";
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return relativeTime.format(-diffMin, "minute");
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return relativeTime.format(-diffHour, "hour");
  return relativeTime.format(-Math.floor(diffHour / 24), "day");
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
  const { messages, loading, connectionState, send } = usePeladaChat(peladaId, token);
  const [inputValue, setInputValue] = useState("");
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const wasNearBottomRef = useRef(true);

  // Remember whether the reader was at the bottom before new messages render
  useLayoutEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;
    const onScroll = () => {
      wasNearBottomRef.current =
        container.scrollTop + container.clientHeight >= container.scrollHeight - 100;
    };
    container.addEventListener("scroll", onScroll);
    return () => container.removeEventListener("scroll", onScroll);
  }, [collapsed]);

  // Stick to the bottom on first load and on new messages while the reader is at the bottom
  useEffect(() => {
    const container = scrollContainerRef.current;
    if (container && !loading && wasNearBottomRef.current) {
      container.scrollTop = container.scrollHeight;
    }
  }, [loading, messages.length]);

  const sendMessage = () => {
    const content = inputValue.trim();
    if (content && send(content)) setInputValue("");
  };

  return (
    <div className="flex flex-col border border-border rounded-lg overflow-hidden h-full min-h-[400px] bg-background">
      {connectionState === "reconnecting" && (
        <p className="shrink-0 bg-muted px-3 py-1 text-center text-xs text-muted-foreground">Reconectando...</p>
      )}
      {connectionState === "failed" && (
        <Alert variant="destructive" className="shrink-0 rounded-none border-x-0 border-t-0 px-3 py-1 text-center">
          <AlertDescription className="text-xs">Sem conexão com o chat. Atualize a página para tentar de novo.</AlertDescription>
        </Alert>
      )}

      {!collapsed && (
        <>
          <div ref={scrollContainerRef} className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto p-3">
            {loading ? (
              [0, 1, 2, 3, 4].map((i) => (
                <div key={i} className="flex items-start gap-2">
                  <Skeleton className="size-7 shrink-0 rounded-full" />
                  <div className="flex flex-1 flex-col gap-1">
                    <Skeleton className="h-3 w-20" />
                    <Skeleton className="h-4 w-40" />
                  </div>
                </div>
              ))
            ) : messages.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">Nenhuma mensagem ainda.</p>
            ) : (
              messages.map((msg) => {
                const isOwn = msg.sender.id === currentUserId;
                return (
                  <div key={msg.id} className={cn("flex items-end gap-2", isOwn ? "flex-row-reverse" : "flex-row")}>
                    {!isOwn && (
                      <Avatar className="size-7 shrink-0">
                        <AvatarImage src={getFileUrl(msg.sender.image)} alt={msg.sender.username} />
                        <AvatarFallback className="text-xs font-semibold">{getInitials(msg.sender.username)}</AvatarFallback>
                      </Avatar>
                    )}
                    <div className={cn("flex max-w-[75%] flex-col", isOwn ? "items-end" : "items-start")}>
                      {!isOwn && <span className="mb-0.5 text-xs text-muted-foreground">{msg.sender.username}</span>}
                      <div
                        className={cn(
                          "break-words rounded-2xl px-3 py-1.5 text-sm",
                          isOwn ? "rounded-br-sm bg-gradient-primary text-white" : "rounded-bl-sm bg-muted text-foreground",
                        )}
                      >
                        {msg.content}
                      </div>
                      <span className="mt-0.5 text-xs text-muted-foreground" title={new Date(msg.sentAt).toLocaleString("pt-BR")}>
                        {formatRelativeTime(msg.sentAt)}
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </div>

          <form
            className="flex shrink-0 items-center gap-2 border-t border-border bg-background px-3 py-2"
            onSubmit={(e) => {
              e.preventDefault();
              sendMessage();
            }}
          >
            <Input
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              placeholder="Escreva uma mensagem..."
              aria-label="Mensagem"
              maxLength={MAX_MESSAGE_LENGTH}
              className="flex-1 rounded-full"
            />
            <Button type="submit" size="icon" variant="ghost" aria-label="Enviar mensagem" disabled={!inputValue.trim()}>
              <SendHorizontal className="size-4" />
            </Button>
          </form>
        </>
      )}
    </div>
  );
}
