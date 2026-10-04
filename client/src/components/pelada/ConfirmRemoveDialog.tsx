import type { PeladaMember } from "../../types/pelada";

export function ConfirmRemoveDialog({
  member,
  onConfirm,
  onClose,
  loading,
}: {
  member: PeladaMember;
  onConfirm: () => void;
  onClose: () => void;
  loading: boolean;
}) {
  return (
    <div
      className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <div
        className="bg-background rounded-lg shadow-lg w-full max-w-sm p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold mb-2">Remover Jogador</h3>
        <p className="text-sm text-muted-foreground mb-6">
          Remover <strong>{member.username}</strong> dessa Pelada?
        </p>
        <div className="flex gap-3 justify-end">
          <button
            className="text-sm text-muted-foreground hover:underline"
            onClick={onClose}
            disabled={loading}
          >
            Cancelar
          </button>
          <button
            className="text-sm bg-destructive text-destructive-foreground px-4 py-2 rounded disabled:opacity-50"
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? "Removendo..." : "Remova"}
          </button>
        </div>
      </div>
    </div>
  );
}
