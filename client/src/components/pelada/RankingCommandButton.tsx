import { useState, useEffect } from "react";
import { HugeiconsIcon } from "@hugeicons/react"
import { CalendarAdd01Icon, CalendarCheckIn01Icon, Clock04Icon, Home01Icon, UserAdd01Icon, UserGroupIcon, UserIcon, UserMinus01Icon } from "@hugeicons/core-free-icons"
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
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
import { AttendanceRankingDialog } from "./AttendanceRankingDialog";
import { ComparePlayersDialog } from "./ComparePlayersDialog";
import { RemovePlayerDialog } from "./RemovePlayerDialog";
import type { PeladaMember } from "@/types/pelada";

interface RankingCommandButtonProps {
  peladaId: number;
  members: PeladaMember[];
  creatorId: number | null;
  isAdmin?: boolean;
  onCreateSession?: () => void;
  onInvitePlayer?: () => void;
  onRemovePlayer?: (member: PeladaMember) => void;
  onOpenHistory: () => void;
}

export function RankingCommandButton({
  peladaId,
  members,
  creatorId,
  isAdmin = false,
  onCreateSession,
  onInvitePlayer,
  onRemovePlayer,
  onOpenHistory,
}: RankingCommandButtonProps) {
  const navigate = useNavigate();
  const [popoverOpen, setPopoverOpen] = useState(false);
  const [compareOpen, setCompareOpen] = useState(false);
  const [removeOpen, setRemoveOpen] = useState(false);
  const [attendanceOpen, setAttendanceOpen] = useState(false);

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
        case "i":
          e.preventDefault();
          run(onOpenHistory);
          break;
        case "p":
          e.preventDefault();
          run(() => setAttendanceOpen(true));
          break;
        case "a":
          if (isAdmin && onInvitePlayer) { e.preventDefault(); run(onInvitePlayer); }
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
  }, [popoverOpen, isAdmin, onInvitePlayer, onRemovePlayer, onCreateSession, onOpenHistory, navigate]);

  return (
    <>
      <Button
        variant="outline"
        className="h-auto gap-1.5 bg-transparent px-3.5 py-[7px] text-sm font-medium hover:bg-accent"
        onClick={() => setPopoverOpen(true)}
      >
        <span aria-hidden>⌘</span>
        Ações
      </Button>

      <CommandDialog open={popoverOpen} onOpenChange={setPopoverOpen}>
        <CommandInput placeholder="Digite um comando ou pesquise..." />
        <CommandList>
          <CommandEmpty>Nenhum resultado encontrado.</CommandEmpty>

          <CommandGroup heading="Jogadores">
            <CommandItem onSelect={() => run(() => setCompareOpen(true))}>
              <HugeiconsIcon icon={UserGroupIcon} />
              Comparar Jogadores
              <CommandShortcut>{mod}J</CommandShortcut>
            </CommandItem>
            <CommandItem onSelect={() => run(onOpenHistory)}>
              <HugeiconsIcon icon={Clock04Icon} />
              Histórico do Jogador
              <CommandShortcut>{mod}I</CommandShortcut>
            </CommandItem>
            <CommandItem onSelect={() => run(() => setAttendanceOpen(true))}>
              <HugeiconsIcon icon={CalendarCheckIn01Icon} />
              Ranking de Presença
              <CommandShortcut>{mod}P</CommandShortcut>
            </CommandItem>
            {isAdmin && onInvitePlayer && (
              <CommandItem onSelect={() => run(onInvitePlayer)}>
                <HugeiconsIcon icon={UserAdd01Icon} />
                Convidar jogador
                <CommandShortcut>{mod}A</CommandShortcut>
              </CommandItem>
            )}
            {isAdmin && onRemovePlayer && (
              <CommandItem
                onSelect={() => run(() => setRemoveOpen(true))}
                className="text-destructive data-[selected=true]:text-destructive"
              >
                <HugeiconsIcon icon={UserMinus01Icon} />
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
                  <HugeiconsIcon icon={CalendarAdd01Icon} />
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
                <HugeiconsIcon icon={UserIcon} />
                {m.username}
              </CommandItem>
            ))}
          </CommandGroup>

          <CommandSeparator />
          <CommandGroup heading="Navegação">
            <CommandItem onSelect={() => run(() => navigate("/home"))}>
              <HugeiconsIcon icon={Home01Icon} />
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
          members={members.filter((m) => m.id !== creatorId)}
          onConfirm={(member) => {
            setRemoveOpen(false);
            onRemovePlayer(member);
          }}
        />
      )}

      <AttendanceRankingDialog
        open={attendanceOpen}
        onOpenChange={setAttendanceOpen}
        peladaId={peladaId}
      />

      <ComparePlayersDialog
        open={compareOpen}
        onOpenChange={setCompareOpen}
        peladaId={peladaId}
        members={members}
      />
    </>
  );
}
