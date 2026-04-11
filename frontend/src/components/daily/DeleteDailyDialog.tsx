import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { deleteDaily } from '@/api/dailies'

const CONFIRMATION_PHRASES = [
  'excluir esta sessão',
  'confirmo a exclusão',
  'remover permanentemente',
  'deletar sessão',
  'tenho certeza disso',
]

interface DeleteDailyDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  dailyId: number
  peladaId: number
  dailyDate: string
}

export default function DeleteDailyDialog({
  open,
  onOpenChange,
  dailyId,
  peladaId,
  dailyDate,
}: DeleteDailyDialogProps) {
  const navigate = useNavigate()
  const [phrase, setPhrase] = useState('')
  const [inputValue, setInputValue] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    if (open) {
      const randomPhrase = CONFIRMATION_PHRASES[Math.floor(Math.random() * CONFIRMATION_PHRASES.length)]
      setPhrase(randomPhrase)
      setInputValue('')
    }
  }, [open])

  const isConfirmed = inputValue === phrase

  async function handleDelete() {
    if (!isConfirmed) return
    setIsLoading(true)
    try {
      await deleteDaily(dailyId)
      onOpenChange(false)
      navigate(`/peladas/${peladaId}`)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-destructive">Excluir Sessão</DialogTitle>
          <DialogDescription>
            Esta ação é irreversível. Todos os dados desta sessão serão permanentemente removidos,
            incluindo resultados, estatísticas e premiações.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <p className="text-sm text-muted-foreground">
            Sessão do dia:{' '}
            <span className="font-medium text-foreground">
              {new Date(dailyDate + 'T00:00:00').toLocaleDateString('pt-BR')}
            </span>
          </p>

          <p className="text-sm">Para confirmar, digite exatamente o texto abaixo:</p>

          <div className="rounded-md bg-destructive/10 border border-destructive/30 px-3 py-2 text-sm font-mono text-destructive select-all">
            {phrase}
          </div>

          <Input
            placeholder="Digite a frase de confirmação"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onPaste={(e) => e.preventDefault()}
          />
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isLoading}>
            Cancelar
          </Button>
          <Button variant="destructive" onClick={handleDelete} disabled={!isConfirmed || isLoading}>
            Excluir Sessão
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
