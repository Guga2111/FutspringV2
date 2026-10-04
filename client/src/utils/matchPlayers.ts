import type { ParsedSession, ParsedPlayer } from './parseSessionMessage'
import type { PlayerDTO } from '../types/daily'

export interface MatchedPlayer extends ParsedPlayer {
  matchedUserId: number | null
  ambiguous: boolean
}

export function autoMatchPlayers(parsed: ParsedSession, members: PlayerDTO[]): MatchedPlayer[][] {
  return parsed.teams.map(team =>
    team.players.map(parsedPlayer => {
      const rawLower = parsedPlayer.rawName.toLowerCase()
      const matched = members.filter(m => {
        const userLower = m.username.toLowerCase()
        return rawLower.includes(userLower) || userLower.includes(rawLower)
      })
      if (matched.length === 1) {
        return { ...parsedPlayer, matchedUserId: matched[0].id, ambiguous: false }
      }
      return { ...parsedPlayer, matchedUserId: null, ambiguous: matched.length > 1 }
    })
  )
}
