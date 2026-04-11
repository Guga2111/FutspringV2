import { useState } from 'react'
import { toast } from 'sonner'
import type { DailyDetail } from '../../types/daily'
import { finalizeDaily } from '../../api/dailies'
import { Button } from '../ui/button'

interface FinalizeModalProps {
  daily: DailyDetail
  onClose: () => void
  onSuccess: (updated: DailyDetail) => void
}

function PlayerCheckboxList({
  players,
  selected,
  onChange,
}: {
  players: DailyDetail['confirmedPlayers']
  selected: number[]
  onChange: (ids: number[]) => void
}) {
  function toggle(id: number) {
    onChange(selected.includes(id) ? selected.filter(x => x !== id) : [...selected, id])
  }

  return (
    <div className="max-h-48 overflow-y-auto rounded-md border border-border divide-y divide-border">
      {players.map(p => (
        <label
          key={p.id}
          className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-muted/50 select-none"
        >
          <input
            type="checkbox"
            className="accent-primary h-4 w-4 flex-shrink-0"
            checked={selected.includes(p.id)}
            onChange={() => toggle(p.id)}
          />
          <span className="text-sm">{p.username}</span>
        </label>
      ))}
    </div>
  )
}

export default function FinalizeModal({ daily, onClose, onSuccess }: FinalizeModalProps) {
  const [puskasIds, setPuskasIds] = useState<number[]>([])
  const [wiltballIds, setWiltballIds] = useState<number[]>([])
  const [loading, setLoading] = useState(false)

  async function handleSubmit() {
    setLoading(true)
    try {
      const updated = await finalizeDaily(daily.id, puskasIds, wiltballIds)
      toast.success('Diária Finalizada')
      onSuccess(updated)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao finalizar diária')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
      <div className="bg-background rounded-lg shadow-lg w-full max-w-md">
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-lg font-semibold">Finalizar Diária</h2>
          <button
            aria-label="Close"
            className="text-muted-foreground hover:text-foreground text-xl leading-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-full"
            onClick={onClose}
          >
            ×
          </button>
        </div>
        <div className="p-4 space-y-4">
          <p className="text-sm text-muted-foreground">
            Selecione os premiados e compute as estatísticas.
          </p>

          <div>
            <label className="text-sm font-medium block mb-1.5">
              Puskas
              {puskasIds.length > 0 && (
                <span className="ml-2 text-xs text-muted-foreground font-normal">
                  ({puskasIds.length} selecionado{puskasIds.length > 1 ? 's' : ''})
                </span>
              )}
            </label>
            <PlayerCheckboxList
              players={daily.confirmedPlayers}
              selected={puskasIds}
              onChange={setPuskasIds}
            />
          </div>

          <div>
            <label className="text-sm font-medium block mb-1.5">
              Bola Murcha
              {wiltballIds.length > 0 && (
                <span className="ml-2 text-xs text-muted-foreground font-normal">
                  ({wiltballIds.length} selecionado{wiltballIds.length > 1 ? 's' : ''})
                </span>
              )}
            </label>
            <PlayerCheckboxList
              players={daily.confirmedPlayers}
              selected={wiltballIds}
              onChange={setWiltballIds}
            />
          </div>
        </div>
        <div className="flex justify-end gap-3 p-4 border-t">
          <Button variant="outline" disabled={loading} onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="gradient" disabled={loading} onClick={handleSubmit}>
            {loading ? 'Finalizando...' : 'Finalizar'}
          </Button>
        </div>
      </div>
    </div>
  )
}
