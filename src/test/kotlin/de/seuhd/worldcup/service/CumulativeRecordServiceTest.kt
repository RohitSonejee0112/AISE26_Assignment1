package de.seuhd.worldcup.service

import de.seuhd.worldcup.model.*
import kotlin.test.*

class CumulativeRecordServiceTest {

    private fun makeMatch(id: Int, home: String, away: String, hs: Int, aws: Int) = Match(
        matchId = id, round = "Test", date = "2026-06-11",
        homeTeam = home, awayTeam = away, homeScore = hs, awayScore = aws,
        ground = "Test", odds = Odds(1.5, 3.5, 5.0)
    )

    @Test
    fun `group stage records are tracked correctly`() {
        val service = CumulativeRecordService()
        val teamA = Team("A", "Team A")
        val teamB = Team("B", "Team B")
        val group = Group("G1", listOf(teamA, teamB), listOf(
            makeMatch(1, "A", "B", 2, 1)
        ))

        service.initFromGroups(listOf(group))
        val recordA = service.getRecord("A")
        val recordB = service.getRecord("B")

        assertNotNull(recordA)
        assertEquals(1, recordA.wins)
        assertEquals(0, recordA.losses)
        assertEquals(3, recordA.points)

        assertNotNull(recordB)
        assertEquals(0, recordB.wins)
        assertEquals(1, recordB.losses)
    }

    @Test
    fun `knockout results add to cumulative records`() {
        val service = CumulativeRecordService()
        val teamA = Team("A", "Team A")
        val teamB = Team("B", "Team B")
        val group = Group("G1", listOf(teamA, teamB), listOf(
            makeMatch(1, "A", "B", 2, 1)
        ))

        service.initFromGroups(listOf(group))
        assertEquals(1, service.getRecord("A")!!.played)

        val knockout = Knockout(
            matchId = 2, round = "Ro32", date = "2026-06-20",
            homePlaceholder = "WA", awayPlaceholder = "WB",
            homeTeam = "A", awayTeam = "B",
            homeScore = 3, awayScore = 0,
            ground = "Stadium", odds = Odds(1.5, 3.5, 5.0)
        )

        service.addKnockoutRound(listOf(knockout))
        assertEquals(2, service.getRecord("A")!!.played)
        assertEquals(2, service.getRecord("A")!!.wins)
        assertEquals(2, service.getRecord("B")!!.played)
        assertEquals(2, service.getRecord("B")!!.losses)
    }

    @Test
    fun `formatRecord shows correct string`() {
        val service = CumulativeRecordService()
        val teamA = Team("A", "Team A")
        val group = Group("G1", listOf(teamA), listOf(
            makeMatch(1, "A", "B", 2, 1)
        ))

        service.initFromGroups(listOf(group))
        val formatted = service.formatRecord("A")
        assertTrue(formatted.contains("1W-0D-0L"))
        assertTrue(formatted.contains("3pts"))
        assertTrue(formatted.contains("GD:+1"))
    }
}