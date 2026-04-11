import { useState } from 'react'
import { CheckCircle, Play, XCircle, ClipboardList, FlagTriangleRight, Pencil, ShieldCheck, FileText, Trash2 } from 'lucide-react'
import type { DailyDetail } from '../../types/daily'
import { Button } from '../ui/button'
import DeleteDailyDialog from './DeleteDailyDialog'

interface AdminActionBarProps {
  daily: DailyDetail
  onConfirmDaily: () => void
  onStartSession: () => void
  onCancelDaily: () => void
  onEnterResults: () => void
  onFinalizeDaily: () => void
  onImportFromMessage: () => void
}

export default function AdminActionBar({
  daily,
  onConfirmDaily,
  onStartSession,
  onCancelDaily,
  onEnterResults,
  onFinalizeDaily,
  onImportFromMessage,
}: AdminActionBarProps) {
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)

  if (!daily.isAdmin) return null
  if (daily.status !== 'SCHEDULED' && daily.status !== 'CONFIRMED' && daily.status !== 'IN_COURSE' && daily.status !== 'FINISHED' && daily.status !== 'CANCELED') return null

  return (
    <>
      <div className="fixed bottom-0 left-0 right-0 z-50 border-t border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="flex items-center gap-3 px-4 py-3 max-w-4xl mx-auto w-full">
          <div className="flex items-center gap-1.5 shrink-0">
            <ShieldCheck className="h-3.5 w-3.5 text-muted-foreground" />
            <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider hidden sm:block">Admin</span>
          </div>
          <div className="h-5 w-px bg-border shrink-0 hidden sm:block" />
          <div className="flex items-center gap-2 overflow-x-auto flex-1 scrollbar-hide sm:justify-center">
            {daily.status === 'SCHEDULED' && (
              <>
                <Button variant="gradient" onClick={onConfirmDaily}>
                  <CheckCircle className="h-4 w-4" />
                  <span className="sm:hidden">Confirmar</span>
                  <span className="hidden sm:inline">Confirmar Diária</span>
                </Button>
                <Button variant="outline" onClick={onImportFromMessage}>
                  <FileText className="h-4 w-4" />
                  <span className="sm:hidden">Mensagem</span>
                  <span className="hidden sm:inline">Importar Mensagem</span>
                </Button>
              </>
            )}
            {daily.status === 'CONFIRMED' && (
              <Button variant="gradient" onClick={onStartSession}>
                <Play className="h-4 w-4" />
                <span className="sm:hidden">Iniciar</span>
                <span className="hidden sm:inline">Iniciar Sessão</span>
              </Button>
            )}
            {(daily.status === 'SCHEDULED' || daily.status === 'CONFIRMED') && (
              <Button
                variant="outline"
                className='text-destructive'
                onClick={onCancelDaily}
              >
                <XCircle className="h-4 w-4 text-destructive" />
                <span className="sm:hidden">Cancelar</span>
                <span className="hidden sm:inline">Cancelar Diária</span>
              </Button>
            )}
            {daily.status === 'IN_COURSE' && (
              <>
                <Button variant="gradient" onClick={onEnterResults}>
                  <ClipboardList className="h-4 w-4" />
                  <span className="sm:hidden">Resultados</span>
                  <span className="hidden sm:inline">Inserir Resultados</span>
                </Button>
                {daily.matches.length > 0 && (
                  <Button variant="outline" onClick={onFinalizeDaily}>
                    <FlagTriangleRight className="h-4 w-4" />
                    <span className="sm:hidden">Finalizar</span>
                    <span className="hidden sm:inline">Finalizar Diária</span>
                  </Button>
                )}
              </>
            )}
            {daily.status === 'FINISHED' && (
              <>
                <Button variant="outline" onClick={onEnterResults}>
                  <Pencil className="h-4 w-4" />
                  <span className="sm:hidden">Editar</span>
                  <span className="hidden sm:inline">Editar Resultados</span>
                </Button>
                <Button variant="outline" onClick={onFinalizeDaily}>
                  <FlagTriangleRight className="h-4 w-4" />
                  <span className="sm:hidden">Re-Finalizar</span>
                  <span className="hidden sm:inline">Re-Finalizar Diária</span>
                </Button>
              </>
            )}
            <Button
              variant="outline"
              className="text-destructive"
              onClick={() => setDeleteDialogOpen(true)}
            >
              <Trash2 className="h-4 w-4 text-destructive" />
              <span className="sm:hidden">Excluir</span>
              <span className="hidden sm:inline">Excluir Sessão</span>
            </Button>
          </div>
        </div>
      </div>

      <DeleteDailyDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        dailyId={daily.id}
        peladaId={daily.peladaId}
        dailyDate={daily.dailyDate}
      />
    </>
  )
}
