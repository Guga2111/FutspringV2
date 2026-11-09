import { describe, expect, it } from "vitest"
import type { PlayerDTO, TeamDTO } from "@/types/daily"
import {
  addTeam,
  dailyTeamPendencies,
  draftFromDaily,
  moveToTeam,
  pendencyLines,
  reconcileDraft,
  removeTeam,
  teamPendencies,
  unassignedPlayers,
  type DraftTeam,
} from "./teamDraft"

const player = (id: number, username = `P${id}`): PlayerDTO => ({ id, username, image: null, stars: 3, position: null, isDeleted: false })
const team = (id: number, name: string, players: PlayerDTO[], color: string | null = null): TeamDTO => ({
  id,
  name,
  color,
  totalStars: 0,
  averageStars: 0,
  players,
})

const draft: DraftTeam[] = [
  { name: "Time 1", color: "#3b82f6", playerIds: [1, 2] },
  { name: "Time 2", color: null, playerIds: [3] },
]

describe("draftFromDaily", () => {
  it("starts from the current teams, keeping names and colors", () => {
    const teams = [team(10, "Azul", [player(1), player(2)], "#3b82f6"), team(11, "Verde", [player(3)])]
    expect(draftFromDaily({ teams, numberOfTeams: 3 })).toEqual([
      { name: "Azul", color: "#3b82f6", playerIds: [1, 2] },
      { name: "Verde", color: null, playerIds: [3] },
    ])
  })

  it("without teams, creates numberOfTeams empty teams", () => {
    expect(draftFromDaily({ teams: [], numberOfTeams: 3 }).map((t) => t.name)).toEqual(["Time 1", "Time 2", "Time 3"])
  })
})

describe("moveToTeam", () => {
  it("moves a player to the end of another team", () => {
    expect(moveToTeam(draft, 1, 1).map((t) => t.playerIds)).toEqual([[2], [3, 1]])
  })

  it("takes a player out of every team with null", () => {
    expect(moveToTeam(draft, 3, null).map((t) => t.playerIds)).toEqual([[1, 2], []])
  })

  it("brings an unassigned player into a team", () => {
    expect(moveToTeam(draft, 4, 0)[0].playerIds).toEqual([1, 2, 4])
  })
})

describe("addTeam / removeTeam", () => {
  it("adds the first free Time N, up to 10", () => {
    expect(addTeam(draft).at(-1)).toEqual({ name: "Time 3", color: null, playerIds: [] })
    const ten = Array.from({ length: 10 }, (_, i) => ({ name: `T${i}`, color: null, playerIds: [] }))
    expect(addTeam(ten)).toHaveLength(10)
  })

  it("skips a name already taken", () => {
    const teams = [...draft, { name: "Time 3", color: null, playerIds: [] }].filter((t) => t.name !== "Time 2")
    expect(addTeam(teams).at(-1)?.name).toBe("Time 4")
  })

  it("removes a team (its players go back to Sem time) but never below two", () => {
    const three = addTeam(draft)
    expect(removeTeam(three, 0).map((t) => t.name)).toEqual(["Time 2", "Time 3"])
    expect(removeTeam(draft, 0)).toBe(draft)
  })
})

describe("unassignedPlayers / reconcileDraft", () => {
  const confirmed = [player(1), player(2), player(3), player(4)]

  it("lists confirmed players on no team, in confirmation order", () => {
    expect(unassignedPlayers(draft, confirmed).map((p) => p.id)).toEqual([4])
  })

  it("drops players who are no longer confirmed and keeps the rest", () => {
    expect(reconcileDraft(draft, [player(1), player(3), player(5)]).map((t) => t.playerIds)).toEqual([[1], [3]])
  })
})

describe("teamPendencies", () => {
  it("reports short teams, players without a team and empty teams", () => {
    const teams = [...draft, { name: "Time 3", color: null, playerIds: [] }]
    const confirmed = [player(1), player(2), player(3), player(4, "Bruno")]
    const pendencies = teamPendencies(teams, confirmed, 2)

    expect(pendencies.short).toEqual([{ name: "Time 2", count: 1 }])
    expect(pendencies.empty).toEqual(["Time 3"])
    expect(pendencies.unassigned.map((p) => p.username)).toEqual(["Bruno"])
    expect(pendencies.blocking).toBe(true)
    expect(pendencyLines(pendencies, 2)).toEqual([
      "Time 3 está vazio",
      "Time 2 desfalcado (1 de 2)",
      "1 confirmado sem time: Bruno",
    ])
  })

  it("short teams and leftovers don't block", () => {
    expect(teamPendencies(draft, [player(1), player(2), player(3), player(4)], 5).blocking).toBe(false)
  })

  it("fewer than two teams blocks", () => {
    const pendencies = teamPendencies([], [player(1)], 5)
    expect(pendencies.blocking).toBe(true)
    expect(pendencyLines(pendencies, 5)[0]).toBe("Monte pelo menos 2 times")
  })

  it("lists up to five names of players without a team", () => {
    const confirmed = Array.from({ length: 7 }, (_, i) => player(i + 10, `J${i}`))
    expect(pendencyLines(teamPendencies(draft, confirmed, 2), 2).at(-1)).toBe(
      "7 confirmados sem time: J0, J1, J2, J3, J4 e mais 2",
    )
  })

  it("reads the daily's own teams", () => {
    const daily = { teams: [team(1, "A", [player(1)]), team(2, "B", [player(2), player(3)])], confirmedPlayers: [player(1), player(2), player(3)], playersPerTeam: 2 }
    expect(dailyTeamPendencies(daily).short).toEqual([{ name: "A", count: 1 }])
  })
})
