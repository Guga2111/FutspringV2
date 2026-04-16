import { Button } from "../ui/button";

export function DeletePeladaDialog({
  peladaName,
  deleting,
  onConfirm,
  onClose,
}: {
  peladaName: string;
  deleting: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
      onClick={() => !deleting && onClose()}
    >
      <div
        className="bg-background rounded-lg shadow-lg w-full max-w-sm p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold mb-2">Deletar Pelada</h3>
        <p className="text-sm text-muted-foreground mb-2">
          Voce tem certeza que quer deletar <strong>{peladaName}</strong>?
        </p>
        <p className="text-sm text-destructive mb-6">
          Essa ação nao pode ser desfeita. Todos os membros e dados vão ser perdidos.
        </p>
        <div className="flex gap-3 justify-end">
          <button
            className="text-sm text-muted-foreground hover:underline"
            onClick={onClose}
            disabled={deleting}
          >
            Cancelar
          </button>
          <Button
            className="text-sm bg-destructive text-destructive-foreground px-4 py-2 rounded disabled:opacity-50 rounded-full"
            onClick={onConfirm}
            disabled={deleting}
          >
            {deleting ? "Deletando..." : "Deletar Pelada"}
          </Button>
        </div>
      </div>
    </div>
  );
}
