import { useRef, useState } from 'react'
import type { DailyStatus } from '@/types/daily'
import type { ResultsMode } from '@/components/daily/hooks/useResultsForm'

export type StatusDialog = { targetStatus: DailyStatus; description: string; title: string; variant?: 'destructive' | 'default' | 'gradient' }

export function useDailyModals() {
  // null: closed; add = new matches (live), edit = every saved match
  const [resultsMode, setResultsMode] = useState<ResultsMode | null>(null)
  const [finalizeOpen, setFinalizeOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [statusDialog, setStatusDialog] = useState<StatusDialog | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  // null: follow the default (open until the teams are sorted)
  const [attendanceOpen, setAttendanceOpen] = useState<boolean | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  return {
    resultsMode,
    openResults: (mode: ResultsMode) => setResultsMode(mode),
    closeResults: () => setResultsMode(null),
    finalizeOpen,
    openFinalize: () => setFinalizeOpen(true),
    closeFinalize: () => setFinalizeOpen(false),
    importOpen,
    openImport: () => setImportOpen(true),
    closeImport: () => setImportOpen(false),
    statusDialog,
    setStatusDialog,
    deleteOpen,
    setDeleteOpen,
    attendanceOpen,
    setAttendanceOpen,
    fileInputRef,
  }
}
