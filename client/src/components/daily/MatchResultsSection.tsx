import { useState } from 'react'
import type { DailyDetail } from '../../types/daily'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { Button } from '../ui/button'

interface MatchResultsSectionProps {
  daily: DailyDetail
}

export default function MatchResultsSection({ daily }: MatchResultsSectionProps) {
  const [expanded, setExpanded] = useState(false)

  if (daily.matches.length === 0) return null

  return (
    <section className="mb-8">
      <div className="flex items-center justify-between mb-3">
        <Button variant="ghost" size="sm" className="h-7 gap-1 text-lg font-semibold px-0 hover:bg-transparent" onClick={() => setExpanded((p) => !p)}>
          {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          Resultado das Partidas
        </Button>
      </div>
      {expanded && <div className="space-y-2">
        {daily.matches.map((match) => {
          const isDraw =
            match.team1Score !== null &&
            match.team2Score !== null &&
            match.team1Score === match.team2Score
          const team1Won = match.winnerId === match.team1Id
          const team2Won = match.winnerId === match.team2Id
          const team1 = daily.teams.find(t => t.id === match.team1Id)
          const team2 = daily.teams.find(t => t.id === match.team2Id)
          const team1Color = team1?.color ?? '#6b7280'
          const team2Color = team2?.color ?? '#6b7280'
          const team1Players = team1?.players ?? []
          const team2Players = team2?.players ?? []

          const team1Scorers = team1Players.flatMap(p => {
            const s = daily.playerStats.find(s => s.userId === p.id)
            if (!s || (s.goals === 0 && s.assists === 0)) return []
            return [{ username: p.username, goals: s.goals, assists: s.assists }]
          })
          const team2Scorers = team2Players.flatMap(p => {
            const s = daily.playerStats.find(s => s.userId === p.id)
            if (!s || (s.goals === 0 && s.assists === 0)) return []
            return [{ username: p.username, goals: s.goals, assists: s.assists }]
          })
          const hasScorers = team1Scorers.length > 0 || team2Scorers.length > 0
          const maxRows = Math.max(team1Scorers.length, team2Scorers.length)

          return (
            <div key={match.id} className="border rounded-lg p-3">
              <div className="flex items-center justify-center gap-3 text-sm">
                <span className={`text-right flex-1 flex items-center justify-end gap-1.5 ${team1Won ? 'font-bold' : ''}`}>
                  {match.team1Name}
                  <span className="w-3 h-3 rounded-full inline-block shrink-0" style={{ backgroundColor: team1Color }} />
                </span>
                <span className="text-muted-foreground tabular-nums flex items-center gap-1">
                  <span>{match.team1Score ?? '–'}</span>
                  <span>–</span>
                  <span>{match.team2Score ?? '–'}</span>
                </span>
                <span className={`text-left flex-1 flex items-center gap-1.5 ${team2Won ? 'font-bold' : ''}`}>
                  <span className="w-3 h-3 rounded-full inline-block shrink-0" style={{ backgroundColor: team2Color }} />
                  {match.team2Name}
                </span>
              </div>
              {isDraw && (
                <p className="text-center text-xs text-muted-foreground mt-1">Empate</p>
              )}
              {hasScorers && (
                <div className="mt-2 flex gap-3 text-xs text-muted-foreground">
                  <div className="flex-1 flex flex-col items-end gap-0.5">
                    {Array.from({ length: maxRows }).map((_, i) => {
                      const p = team1Scorers[i]
                      if (!p) return <div key={i} className="h-4" />
                      return (
                        <div key={i} className="flex items-center gap-1">
                          {p.goals > 0 && <><span>{p.username}</span><span>{Array.from({ length: p.goals }).map((_, j) => <span key={j}>⚽</span>)}</span></>}
                          {p.assists > 0 && p.goals === 0 && <><span>{p.username}</span><span>{Array.from({ length: p.assists }).map((_, j) => <span key={j}>🅰️</span>)}</span></>}
                        </div>
                      )
                    })}
                  </div>
                  <div className="flex-1 flex flex-col items-start gap-0.5">
                    {Array.from({ length: maxRows }).map((_, i) => {
                      const p = team2Scorers[i]
                      if (!p) return <div key={i} className="h-4" />
                      return (
                        <div key={i} className="flex items-center gap-1">
                          {p.goals > 0 && <><span>{p.username}</span><span>{Array.from({ length: p.goals }).map((_, j) => <span key={j}>⚽</span>)}</span></>}
                          {p.assists > 0 && p.goals === 0 && <><span>{p.username}</span><span>{Array.from({ length: p.assists }).map((_, j) => <span key={j}>🅰️</span>)}</span></>}
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>}
    </section>
  )
}
