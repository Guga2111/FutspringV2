import { useState, useRef } from "react";
import { toast } from "sonner";
import { addPlayer, searchUsers } from "../../api/peladas";
import type { UserResponseDTO } from "../../types/auth";

export function AddPlayerDialog({
  peladaId,
  existingMemberIds,
  onClose,
  onAdded,
}: {
  peladaId: number;
  existingMemberIds: Set<number>;
  onClose: () => void;
  onAdded: () => void;
}) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserResponseDTO[]>([]);
  const [searching, setSearching] = useState(false);
  const [adding, setAdding] = useState<number | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleQueryChange = (q: string) => {
    setQuery(q);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!q.trim()) {
      setResults([]);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      setSearching(true);
      try {
        const data = await searchUsers(q.trim());
        setResults(data);
      } catch {
        // ignore
      } finally {
        setSearching(false);
      }
    }, 300);
  };

  const handleAdd = async (user: UserResponseDTO) => {
    setAdding(user.id);
    try {
      await addPlayer(peladaId, user.id);
      toast.success(`${user.username} adicionado na pelada`);
      onAdded();
      onClose();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      toast.error(e?.response?.data?.message ?? "Falha ao adicionar jogador");
    } finally {
      setAdding(null);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <div
        className="bg-background rounded-lg shadow-lg w-full max-w-md p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold mb-4">Adicionar Jogador</h3>
        <input
          className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm mb-3"
          placeholder="Procure pelo nome de usuário ou pelo email..."
          value={query}
          onChange={(e) => handleQueryChange(e.target.value)}
          autoFocus
        />
        {searching && (
          <p className="text-sm text-muted-foreground mb-2">Procurando...</p>
        )}
        <div className="space-y-2 max-h-64 overflow-y-auto">
          {results.map((user) => {
            const alreadyMember = existingMemberIds.has(user.id);
            return (
              <div
                key={user.id}
                className="flex items-center gap-3 p-2 rounded hover:bg-muted"
              >
                <div className="h-8 w-8 rounded-full bg-muted flex items-center justify-center text-xs font-semibold flex-shrink-0">
                  {user.username.slice(0, 2).toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">
                    {user.username}
                  </p>
                  <p className="text-xs text-muted-foreground truncate">
                    {user.email}
                  </p>
                </div>
                {alreadyMember ? (
                  <span className="text-xs text-muted-foreground">Membro</span>
                ) : (
                  <button
                    className="text-xs bg-primary text-primary-foreground px-3 py-1 rounded disabled:opacity-50"
                    disabled={adding === user.id}
                    onClick={() => handleAdd(user)}
                  >
                    {adding === user.id ? "..." : "Adicionar"}
                  </button>
                )}
              </div>
            );
          })}
          {!searching && query.trim() && results.length === 0 && (
            <p className="text-sm text-muted-foreground text-center py-4">
              Nenhum usuário encontrado
            </p>
          )}
        </div>
        <div className="flex justify-end mt-4">
          <button
            className="text-sm text-muted-foreground hover:underline"
            onClick={onClose}
          >
            Fechar
          </button>
        </div>
      </div>
    </div>
  );
}
