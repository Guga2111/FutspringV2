import { Plus, X } from "lucide-react"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog"
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "@/components/ui/drawer"
import { ResultsMatchCard } from "@/components/daily/ResultsMatchCard"
import { useResultsForm, type ResultsMode } from "@/components/daily/hooks/useResultsForm"
import { useIsMobile } from "@/hooks/useIsMobile"
import { cn } from "@/lib/utils"
import type { DailyDetail } from "@/types/daily"

interface ResultsDialogProps {
  daily: DailyDetail
  mode: ResultsMode
  onClose: () => void
  onSaved: (updated: DailyDetail) => void
}

const DESCRIPTION = "Marque os times, o placar e, se quiser, quem fez os gols e as assistências."

// Dialog on desktop, bottom drawer on mobile; mounted when opened so every open starts from the saved data
export function ResultsDialog({ daily, mode, onClose, onSaved }: ResultsDialogProps) {
  const isMobile = useIsMobile()
  const { form, fieldArray, firstNumber, addMatch, submit } = useResultsForm(daily, mode, onSaved)
  const submitting = form.formState.isSubmitting
  const count = fieldArray.fields.length
  const close = () => !submitting && onClose()
  const title = mode === "edit" ? "Editar resultados" : "Lançar resultados"

  const body = (
    <form onSubmit={submit} noValidate className="flex min-h-0 flex-1 flex-col">
      <div className={cn("flex items-start gap-3", isMobile ? "pb-3 pl-4 pr-3 pt-4" : "px-5 pb-3.5 pt-5")}>
        <div className="flex flex-1 flex-col gap-1">
          {isMobile ? (
            <>
              <DrawerTitle className="text-lg font-bold">{title}</DrawerTitle>
              <DrawerDescription className="text-[13px] text-muted-foreground">{DESCRIPTION}</DrawerDescription>
            </>
          ) : (
            <>
              <DialogTitle className="text-lg font-bold">{title}</DialogTitle>
              <DialogDescription className="text-[13px] text-muted-foreground">{DESCRIPTION}</DialogDescription>
            </>
          )}
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label="Fechar"
          onClick={close}
          className="size-8 shrink-0 text-muted-foreground hover:bg-accent hover:text-foreground [&_svg]:size-[18px]"
        >
          <X />
        </Button>
      </div>

      <div className={cn("flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto", isMobile ? "px-3 pb-3" : "px-5 pb-4")}>
        {fieldArray.fields.map((field, index) => (
          <ResultsMatchCard
            key={field.id}
            daily={daily}
            index={index}
            number={firstNumber + index}
            control={form.control}
            setValue={form.setValue}
            compact={isMobile}
            canRemove={count > 1}
            onRemove={() => fieldArray.remove(index)}
          />
        ))}
        <Button
          type="button"
          variant="outline"
          onClick={addMatch}
          className="h-11 shrink-0 gap-2 rounded-xl border-dashed border-star-off bg-transparent text-secondary-foreground hover:bg-accent hover:text-foreground"
        >
          <Plus className="size-[15px]" strokeWidth={2.2} />
          Adicionar partida
        </Button>
        {form.formState.errors.root && (
          <Alert variant="destructive">
            <AlertDescription>{form.formState.errors.root.message}</AlertDescription>
          </Alert>
        )}
        {form.formState.errors.matches?.message && (
          <Alert variant="destructive">
            <AlertDescription>{form.formState.errors.matches.message}</AlertDescription>
          </Alert>
        )}
      </div>

      <div className={cn("flex items-center justify-end gap-2 border-t", isMobile ? "p-3" : "px-5 py-3.5")}>
        {!isMobile && (
          <span className="mr-auto text-[13px] text-subtle-foreground">
            {count} {count === 1 ? "partida" : "partidas"}
          </span>
        )}
        <Button type="button" variant="outline" onClick={close} className={cn("h-[42px] bg-transparent px-4 hover:bg-accent", isMobile && "flex-1")}>
          Cancelar
        </Button>
        <Button
          type="submit"
          variant="gradient"
          disabled={submitting}
          className={cn("h-[42px] whitespace-nowrap px-[18px] font-semibold", isMobile && "flex-1")}
        >
          {submitting ? "Salvando…" : "Salvar resultados"}
        </Button>
      </div>
    </form>
  )

  if (isMobile) {
    return (
      <Drawer open onOpenChange={(open) => !open && close()}>
        <DrawerContent className="max-h-[calc(100%-24px)] rounded-t-[18px] bg-popover shadow-dialog">{body}</DrawerContent>
      </Drawer>
    )
  }

  return (
    <Dialog open onOpenChange={(open) => !open && close()}>
      <DialogContent className="flex max-h-[calc(100vh-32px)] w-[min(720px,calc(100%-24px))] max-w-none flex-col gap-0 overflow-hidden rounded-2xl border-input bg-popover p-0 shadow-dialog sm:rounded-2xl [&>button:last-child]:hidden">
        {body}
      </DialogContent>
    </Dialog>
  )
}
