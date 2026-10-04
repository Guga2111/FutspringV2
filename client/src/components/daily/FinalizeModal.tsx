import { useMemo, useState } from "react"
import { toast } from "sonner"
import type { DailyDetail, PlayerDTO } from "@/types/daily"
import { finalizeDaily } from "@/api/dailies"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel, FieldLegend, FieldSet } from "@/components/ui/field"
import { getErrorMessage } from "@/lib/errors"
import { usePlayerSelection } from "@/components/daily/hooks/usePlayerSelection"

interface FinalizeModalProps {
  daily: DailyDetail
  onClose: () => void
  onSuccess: (updated: DailyDetail) => void
}

function PlayerCheckboxList({
  name,
  legend,
  players,
  selected,
  toggle,
}: {
  name: string
  legend: string
  players: PlayerDTO[]
  selected: number[]
  toggle: (id: number) => void
}) {
  return (
    <FieldSet className="gap-1.5">
      <FieldLegend variant="label" className="mb-1.5">
        {legend}
        {selected.length > 0 && (
          <span className="ml-2 text-xs font-normal text-muted-foreground">
            ({selected.length} selecionado{selected.length > 1 ? "s" : ""})
          </span>
        )}
      </FieldLegend>
      <div className="max-h-48 divide-y divide-border overflow-y-auto rounded-md border border-border">
        {players.map((p) => {
          const id = `${name}-${p.id}`
          return (
            <Field key={p.id} orientation="horizontal" className="px-3 py-2 hover:bg-muted/50">
              <Checkbox id={id} checked={selected.includes(p.id)} onCheckedChange={() => toggle(p.id)} />
              <FieldLabel htmlFor={id} className="cursor-pointer font-normal">
                {p.username}
              </FieldLabel>
            </Field>
          )
        })}
      </div>
    </FieldSet>
  )
}

export default function FinalizeModal({ daily, onClose, onSuccess }: FinalizeModalProps) {
  const puskas = usePlayerSelection()
  const wiltball = usePlayerSelection()
  const [loading, setLoading] = useState(false)
  // Awards go to players on the teams (the backend's session players), not the confirmed list
  const sessionPlayers = useMemo(() => daily.teams.flatMap((t) => t.players), [daily.teams])

  async function handleSubmit() {
    setLoading(true)
    try {
      const updated = await finalizeDaily(daily.id, puskas.selected, wiltball.selected)
      toast.success("Sessão finalizada")
      onSuccess(updated)
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível finalizar a sessão"))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !loading && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Finalizar sessão</DialogTitle>
          <DialogDescription>Escolha os premiados; as estatísticas e o ranking serão calculados.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <PlayerCheckboxList
            name="puskas"
            legend="Puskás"
            players={sessionPlayers}
            selected={puskas.selected}
            toggle={puskas.toggle}
          />
          <PlayerCheckboxList
            name="bola-murcha"
            legend="Bola Murcha"
            players={sessionPlayers}
            selected={wiltball.selected}
            toggle={wiltball.toggle}
          />
        </div>
        <DialogFooter className="gap-2">
          <Button variant="outline" disabled={loading} onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="gradient" disabled={loading} onClick={handleSubmit}>
            {loading ? "Finalizando..." : "Finalizar"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
