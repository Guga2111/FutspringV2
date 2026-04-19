import { useState, useEffect } from "react";
import { Users } from "lucide-react";
import { Button } from "../ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "../ui/popover";
import {
  Command,
  CommandList,
  CommandGroup,
  CommandItem,
} from "../ui/command";
import { ComparePlayersDialog } from "./ComparePlayersDialog";
import type { PeladaMember } from "@/types/pelada";

interface RankingCommandButtonProps {
  peladaId: number;
  members: PeladaMember[];
  getFileUrl: (path: string | null | undefined) => string | undefined;
}

export function RankingCommandButton({ peladaId, members, getFileUrl }: RankingCommandButtonProps) {
  const [popoverOpen, setPopoverOpen] = useState(false);
  const [compareOpen, setCompareOpen] = useState(false);

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

  return (
    <>
      <Popover open={popoverOpen} onOpenChange={setPopoverOpen}>
        <PopoverTrigger asChild>
          <Button variant="outline" size="sm" className="gap-2">
            <span className="text-xs">⌘</span>
            Ações
          </Button>
        </PopoverTrigger>
        <PopoverContent className="p-0 w-48" align="end">
          <Command>
            <CommandList>
              <CommandGroup>
                <CommandItem
                  onSelect={() => {
                    setPopoverOpen(false);
                    setCompareOpen(true);
                  }}
                  className="cursor-pointer"
                >
                  <Users className="h-4 w-4 mr-2" />
                  Comparar
                </CommandItem>
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>

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
