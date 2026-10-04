import { useRef, useState } from 'react'

type StatusDialog = { targetStatus: string; description: string; title: string; variant?: 'destructive' | 'default' | 'gradient' }

export function useDailyModals() {
  const [resultsOpen, setResultsOpen] = useState(false)
  const [finalizeOpen, setFinalizeOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [uploadLoading, setUploadLoading] = useState(false)
  const [statusDialog, setStatusDialog] = useState<StatusDialog | null>(null)
  const [statusLoading, setStatusLoading] = useState(false)
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
    uploadLoading,
    setUploadLoading,
    statusDialog,
    setStatusDialog,
    statusLoading,
    setStatusLoading,
    fileInputRef,
  }
}
