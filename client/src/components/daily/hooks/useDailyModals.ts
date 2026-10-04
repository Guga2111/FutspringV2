import { useRef, useState } from 'react'
import type { DailyStatus } from '@/types/daily'

export type StatusDialog = { targetStatus: DailyStatus; description: string; title: string; variant?: 'destructive' | 'default' | 'gradient' }

export function useDailyModals() {
  const [resultsOpen, setResultsOpen] = useState(false)
  const [finalizeOpen, setFinalizeOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [statusDialog, setStatusDialog] = useState<StatusDialog | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  // null: follow the default (open until the teams are sorted)
  const [attendanceOpen, setAttendanceOpen] = useState<boolean | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  return {
    resultsOpen,
    openResults: () => setResultsOpen(true),
    closeResults: () => setResultsOpen(false),
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
