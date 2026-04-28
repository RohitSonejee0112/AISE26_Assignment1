package de.seuhd.worldcup.service

import de.seuhd.worldcup.model.*
import kotlin.test.*

class StandingsServiceTest {

    private val service = StandingsService()

    private fun makeMatch(id: Int, home: String, away: String, hs: Int?, aws: Int?) = Match(
        matchId = id, round = "Test", date = "2026-06-11",
        homeTeam = home, awayTeam = away, homeScore = hs, awayScore = aws,
        ground = "Test Ground", odds = Odds(1.5, 3.5, 5.0)
    )

    @Test
    fun `calculate standings for a simple group`() {
        val mexico = Team(id = "MEX", name = "Mexico")
        val rsa = Team(id = "RSA", name = "South Africa")
        val match = makeMatch(1, "MEX", "RSA", 2, 1)
        val group = Group("Group A", listOf(mexico, rsa), listOf(match))

        val standings = service.calculateStandings(group)

        assertEquals(2, standings.size)
        val first = standings[0]
        assertEquals("MEX", first.teamId)
        assertEquals(1, first.played)
        assertEquals(1, first.wins)
        assertEquals(3, first.points)
        assertEquals(1, first.goalDifference)
    }

    @Test
    fun `goal difference is used as tie breaker`() {
        val teamA = Team(id = "A", name = "Team A")
        val teamB = Team(id = "B", name = "Team B")
        val match1 = makeMatch(1, "A", "B", 3, 0)
        val match2 = makeMatch(2, "B", "A", 1, 0)
        val group = Group("Group X", listOf(teamA, teamB), listOf(match1, match2))

        val standings = service.calculateStandings(group)
        assertEquals("A", standings[0].teamId)
        assertEquals("B", standings[1].teamId)
    }

    @Test
    fun `empty standings returns all zeros`() {
        val teamA = Team(id = "A", name = "Team A")
        val teamB = Team(id = "B", name = "Team B")
        val group = Group("Group X", listOf(teamA, teamB), emptyList())

        val standings = service.calculateEmptyStandings(group)
        assertEquals(2, standings.size)
        assertEquals(0, standings[0].points)
        assertEquals(0, standings[1].points)
    }
}