import type { DailyDetail } from '../../types/daily'
import { Trophy, Medal } from 'lucide-react'

interface LeagueTableSectionProps {
  daily: DailyDetail
}

export default function LeagueTableSection({ daily }: LeagueTableSectionProps) {
  if (daily.leagueTable.length === 0) return null

  return (
    <section className="mb-8">
      <h2 className="text-lg font-semibold mb-3">Tabela da Liga</h2>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-muted-foreground text-xs">
              <th className="text-left pb-2 pr-2 font-medium">#</th>
              <th className="text-left pb-2 font-medium">Time</th>
              <th className="text-center pb-2 px-2 font-medium">V</th>
              <th className="text-center pb-2 px-2 font-medium">E</th>
              <th className="text-center pb-2 px-2 font-medium">D</th>
              <th className="text-center pb-2 px-2 font-medium">GF</th>
              <th className="text-center pb-2 px-2 font-medium">GA</th>
              <th className="text-center pb-2 px-2 font-medium">GD</th>
              <th className="text-center pb-2 px-2 font-medium">Pts</th>
            </tr>
          </thead>
          <tbody>
            {daily.leagueTable.map((entry) => {
              const isFirst = entry.position === 1
              return (
                <tr
                  key={entry.teamId}
                  className={`border-b last:border-0 ${isFirst ? 'bg-yellow-50 dark:bg-yellow-950/20' : ''}`}
                >
                  <td className="py-2 pr-2 text-muted-foreground">
                    {entry.position === 1 ? (
                      <Trophy className="h-4 w-4 text-yellow-500" />
                    ) : entry.position === 2 ? (
                      <Medal className="h-4 w-4 text-slate-400" />
                    ) : entry.position === 3 ? (
                      <Medal className="h-4 w-4 text-amber-600" />
                    ) : (
                      entry.position
                    )}
                  </td>
                  <td className={`py-2 ${isFirst ? 'font-semibold' : ''}`}>{entry.teamName}</td>
                  <td className="py-2 text-center px-2">{entry.wins}</td>
                  <td className="py-2 text-center px-2">{entry.draws}</td>
                  <td className="py-2 text-center px-2">{entry.losses}</td>
                  <td className="py-2 text-center px-2">{entry.goalsFor}</td>
                  <td className="py-2 text-center px-2">{entry.goalsAgainst}</td>
                  <td className="py-2 text-center px-2">
                    {entry.goalDiff > 0 ? `+${entry.goalDiff}` : entry.goalDiff}
                  </td>
                  <td className="py-2 text-center px-2 font-semibold">{entry.points}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}
