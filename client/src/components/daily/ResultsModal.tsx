import type { DailyDetail } from '../../types/daily'
import { useMatchResults } from './hooks/useMatchResults'
import { ResultsForm } from './ResultsForm'

interface ResultsModalProps {
  daily: DailyDetail
  onClose: () => void
  onSuccess: (updated: DailyDetail) => void
}

export default function ResultsModal({ daily, onClose, onSuccess }: ResultsModalProps) {
  const { rows, loading, updateRow, updateScore, updateStat, addMatch, removeMatch, handleSubmit } =
    useMatchResults(daily, onSuccess)

  return (
    <ResultsForm
      daily={daily}
      rows={rows}
      loading={loading}
      onClose={onClose}
      onUpdateRow={updateRow}
      onUpdateScore={updateScore}
      onUpdateStat={updateStat}
      onAddMatch={addMatch}
      onRemoveMatch={removeMatch}
      onSubmit={handleSubmit}
    />
  )
}
