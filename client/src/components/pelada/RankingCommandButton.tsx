import { useState, useEffect } from "react";
import { Users, CalendarPlus, UserPlus, UserMinus, Home, User } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Button } from "../ui/button";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
  CommandShortcut,
} from "@/components/ui/command";

const isMac = navigator.platform.toUpperCase().includes("MAC");
const mod = isMac ? "⌘" : "Ctrl";
import { ComparePlayersDialog } from "./ComparePlayersDialog";
import { RemovePlayerDialog } from "./RemovePlayerDialog";
import type { PeladaMember } from "@/types/pelada";

interface RankingCommandButtonProps {
  peladaId: number;
  members: PeladaMember[];
  getFileUrl: (path: string | null | undefined) => string | undefined;
  isAdmin?: boolean;
  onCreateSession?: () => void;
  onAddPlayer?: () => void;
  onRemovePlayer?: (member: PeladaMember) => void;
}

export function RankingCommandButton({
  peladaId,
  members,
  getFileUrl,
  isAdmin = false,
  onCreateSession,
  onAddPlayer,
  onRemovePlayer,
}: RankingCommandButtonProps) {
  const navigate = useNavigate();
  const [popoverOpen, setPopoverOpen] = useState(false);
  const [compareOpen, setCompareOpen] = useState(false);
  const [removeOpen, setRemoveOpen] = useState(false);

  const run = (fn: () => void) => {
    setPopoverOpen(false);
    fn();
  };

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setPopoverOpen((v) => !v);
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, []);

  useEffect(() => {
    if (!popoverOpen) return;
    const handler = (e: KeyboardEvent) => {
      if (!(e.metaKey || e.ctrlKey)) return;
      switch (e.key.toLowerCase()) {
        case "j":
          e.preventDefault();
          run(() => setCompareOpen(true));
          break;
        case "a":
          if (isAdmin && onAddPlayer) { e.preventDefault(); run(onAddPlayer); }
          break;
        case "r":
          if (isAdmin && onRemovePlayer) { e.preventDefault(); run(() => setRemoveOpen(true)); }
          break;
        case "e":
          if (isAdmin && onCreateSession) { e.preventDefault(); run(onCreateSession); }
          break;
        case "h":
          e.preventDefault();
          run(() => navigate("/home"));
          break;
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [popoverOpen, isAdmin, onAddPlayer, onRemovePlayer, onCreateSession]);

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        className="gap-2"
        onClick={() => setPopoverOpen(true)}
      >
        <span className="text-xs">⌘</span>
        Ações
      </Button>

      <CommandDialog open={popoverOpen} onOpenChange={setPopoverOpen}>
        <CommandInput placeholder="Digite um comando ou pesquise..." />
        <CommandList>
          <CommandEmpty>Nenhum resultado encontrado.</CommandEmpty>

          <CommandGroup heading="Jogadores">
            <CommandItem onSelect={() => run(() => setCompareOpen(true))}>
              <Users />
              Comparar Jogadores
              <CommandShortcut>{mod}J</CommandShortcut>
            </CommandItem>
            {isAdmin && onAddPlayer && (
              <CommandItem onSelect={() => run(onAddPlayer)}>
                <UserPlus />
                Adicionar Jogador
                <CommandShortcut>{mod}A</CommandShortcut>
              </CommandItem>
            )}
            {isAdmin && onRemovePlayer && (
              <CommandItem
                onSelect={() => run(() => setRemoveOpen(true))}
                className="text-destructive data-[selected=true]:text-destructive"
              >
                <UserMinus />
                Remover Jogador
                <CommandShortcut className="text-destructive">{mod}R</CommandShortcut>
              </CommandItem>
            )}
          </CommandGroup>

          {isAdmin && onCreateSession && (
            <>
              <CommandSeparator />
              <CommandGroup heading="Sessões">
                <CommandItem onSelect={() => run(onCreateSession)}>
                  <CalendarPlus />
                  Nova Sessão
                  <CommandShortcut>{mod}E</CommandShortcut>
                </CommandItem>
              </CommandGroup>
            </>
          )}

          <CommandSeparator />
          <CommandGroup heading="Perfis">
            {members.map((m) => (
              <CommandItem
                key={m.id}
                onSelect={() => run(() => navigate(`/profile/${m.id}`))}
              >
                <User />
                {m.username}
              </CommandItem>
            ))}
          </CommandGroup>

          <CommandSeparator />
          <CommandGroup heading="Navegação">
            <CommandItem onSelect={() => run(() => navigate("/home"))}>
              <Home />
              Início
              <CommandShortcut>{mod}H</CommandShortcut>
            </CommandItem>
          </CommandGroup>
        </CommandList>
      </CommandDialog>

      {onRemovePlayer && (
        <RemovePlayerDialog
          open={removeOpen}
          onOpenChange={setRemoveOpen}
          members={members}
          onConfirm={(member) => {
            setRemoveOpen(false);
            onRemovePlayer(member);
          }}
        />
      )}

      <ComparePlayersDialog
        open={compareOpen}
        onOpenChange={setCompareOpen}
        peladaId={peladaId}
        members={members}
        getFileUrl={getFileUrl}
      />
    </>
  );
}
