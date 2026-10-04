export interface ParsedPlayer {
  rawName: string
  totalGoals: number
  totalAssists: number
}

export interface ParsedTeam {
  colorName: string
  colorHex: string
  players: ParsedPlayer[]
}

export interface ParsedMatch {
  team1ColorName: string
  team1Score: number
  team2ColorName: string
  team2Score: number
}

export interface ParsedSession {
  teams: ParsedTeam[]
  matches: ParsedMatch[]
}

const COLOR_MAP: Record<string, string> = {
  azul: '#3b82f6',
  branco: '#f8fafc',
  preto: '#1e293b',
  vermelho: '#ef4444',
  verde: '#22c55e',
  amarelo: '#eab308',
}

const COLOR_NAMES = Object.keys(COLOR_MAP)

function toTitleCase(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase()
}

function stripEmojis(text: string): string {
  return text
    .replace(/\p{Extended_Pictographic}/gu, '')
    .replace(/\uFE0F/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function countGoals(text: string): number {
  return (text.match(/\u26BD/gu) ?? []).length
}

function countAssists(text: string): number {
  return (text.match(/\u{1F170}/gu) ?? []).length
}

const MATCH_REGEX = /^(\w+)\s+(\d+)\s*[xX]\s*(\d+)\s+(\w+)$/

export function parseSessionMessage(text: string): ParsedSession {
  const lines = text.split('\n').map(l => l.trim()).filter(l => l.length > 0)

  const teams: ParsedTeam[] = []
  const matches: ParsedMatch[] = []

  let currentTeam: ParsedTeam | null = null

  for (const line of lines) {
    // Check match result line first (e.g. "Azul 2 x 0 Preto")
    const matchResult = MATCH_REGEX.exec(line)
    if (matchResult) {
      const w1 = matchResult[1].toLowerCase()
      const w2 = matchResult[4].toLowerCase()
      if (COLOR_NAMES.includes(w1) && COLOR_NAMES.includes(w2)) {
        if (currentTeam) {
          teams.push(currentTeam)
          currentTeam = null
        }
        matches.push({
          team1ColorName: toTitleCase(w1),
          team1Score: parseInt(matchResult[2], 10),
          team2ColorName: toTitleCase(w2),
          team2Score: parseInt(matchResult[3], 10),
        })
        continue
      }
    }

    // Check team header (first word is a color name)
    const firstWord = line.split(/\s+/)[0].toLowerCase()
    if (COLOR_NAMES.includes(firstWord)) {
      if (currentTeam) {
        teams.push(currentTeam)
      }
      currentTeam = {
        colorName: toTitleCase(firstWord),
        colorHex: COLOR_MAP[firstWord],
        players: [],
      }
      continue
    }

    // Player line
    if (currentTeam) {
      const rawName = stripEmojis(line)
      if (rawName) {
        currentTeam.players.push({
          rawName,
          totalGoals: countGoals(line),
          totalAssists: countAssists(line),
        })
      }
    }
  }

  // Flush last team
  if (currentTeam) {
    teams.push(currentTeam)
  }

  return { teams, matches }
}
