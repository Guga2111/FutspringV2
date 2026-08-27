import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "../ui/dialog";
import { UserMinus } from "lucide-react";
import type { PeladaMember } from "@/types/pelada";

interface RemovePlayerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  members: PeladaMember[];
  onConfirm: (member: PeladaMember) => void;
}

export function RemovePlayerDialog({
  open,
  onOpenChange,
  members,
  onConfirm,
}: RemovePlayerDialogProps) {
  const [search, setSearch] = useState("");

  const filtered = members.filter((m) =>
    m.username.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <Dialog open={open} onOpenChange={(v) => { onOpenChange(v); setSearch(""); }}>
      <DialogContent className="max-w-sm bg-zinc-950 border-zinc-800 text-white">
        <DialogHeader>
          <DialogTitle className="text-white">Remover Jogador</DialogTitle>
        </DialogHeader>

        <input
          autoFocus
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Pesquisar jogador..."
          className="w-full rounded-lg bg-zinc-900 border border-zinc-700 px-3 py-2 text-sm text-white placeholder:text-zinc-500 outline-none focus:border-zinc-500"
        />

        <div className="flex flex-col gap-1 max-h-64 overflow-y-auto">
          {filtered.length === 0 ? (
            <p className="text-center text-sm text-zinc-500 py-4">
              Nenhum jogador encontrado.
            </p>
          ) : (
            filtered.map((m) => (
              <button
                key={m.id}
                onClick={() => onConfirm(m)}
                className="flex items-center gap-3 w-full px-3 py-2 rounded-lg text-sm text-left hover:bg-zinc-800 text-zinc-200 hover:text-red-400 transition-colors group"
              >
                <UserMinus className="h-4 w-4 text-zinc-500 group-hover:text-red-400 shrink-0" />
                {m.username}
              </button>
            ))
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
