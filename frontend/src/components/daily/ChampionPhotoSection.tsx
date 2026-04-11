import type { RefObject } from 'react'
import type { DailyDetail } from '../../types/daily'
import { getFileUrl } from '../../lib/utils'
import { Button } from '../ui/button'
import { Upload } from 'lucide-react'

interface ChampionPhotoSectionProps {
  daily: DailyDetail
  fileInputRef: RefObject<HTMLInputElement | null>
  uploadLoading: boolean
  onUploadClick: () => void
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void
}

export default function ChampionPhotoSection({
  daily,
  fileInputRef,
  uploadLoading,
  onUploadClick,
  onChange,
}: ChampionPhotoSectionProps) {
  return (
    <>
      {daily.championImage && (
        <div className="mb-8 -mx-4 sm:mx-0">
          <img
            src={getFileUrl(daily.championImage)}
            alt="Foto do Campeão"
            className="w-full sm:rounded-xl object-cover max-h-80"
          />
        </div>
      )}
      {daily.isAdmin && (
        <section className="mb-8">
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="hidden"
            onChange={onChange}
          />
          {daily.championImage ? (
            <Button
              className="text-sm underline disabled:opacity-50"
              disabled={uploadLoading}
              variant="outline"
              onClick={onUploadClick}
            >
              <Upload className="h-4 w-4" />
              {uploadLoading ? 'Subindo...' : 'Recoloque Foto do Campeão'}
            </Button>
          ) : (
            <div>
              <p className="text-sm text-muted-foreground mb-2">Sem foto do campeão upada ainda.</p>
              <Button
                className="text-sm px-4 py-2 rounded-full disabled:opacity-50"
                disabled={uploadLoading}
                onClick={onUploadClick}
                variant="outline"
              >
                <Upload className="h-4 w-4" />
                {uploadLoading ? 'Subindo...' : 'Suba Foto do Campeão'}
              </Button>
            </div>
          )}
        </section>
      )}
      {(() => {
        if (daily.leagueTable.length === 0) return null
        const champion = daily.leagueTable.find((e) => e.position === 1)
        if (!champion) return null
        const championTeam = daily.teams.find((t) => t.id === champion.teamId)
        const totalGames = champion.wins + champion.draws + champion.losses
        const winPct = totalGames > 0 ? ((champion.wins / totalGames) * 100).toFixed(1) : '0.0'
        return (
          <section className="mb-8">
            <h2 className="text-lg font-semibold mb-3">Time Campeão</h2>
            <div className="border rounded-lg p-4">
              <p className="font-bold text-base mb-1">{champion.teamName}</p>
              {championTeam && championTeam.players.length > 0 && (
                <p className="text-sm text-muted-foreground mb-3">
                  {championTeam.players.map((p) => p.username).join(', ')}
                </p>
              )}
              <div className="flex gap-4 text-sm">
                <span><span className="font-semibold">{champion.wins}</span> vitórias</span>
                <span><span className="font-semibold">{champion.losses}</span> derrotas</span>
                <span><span className="font-semibold">{winPct}%</span> aproveitamento</span>
              </div>
            </div>
          </section>
        )
      })()}
    </>
  )
}
