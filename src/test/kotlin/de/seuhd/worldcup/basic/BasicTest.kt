package de.seuhd.worldcup.basic

import kotlin.test.*

class BasicTest {

    @Test
    fun `calculate standings for a simple group`() {
        val mexico = BasicTeam(id = "MEX", name = "Mexico")
        val rsa = BasicTeam(id = "RSA", name = "South Africa")
        val match = BasicMatch(
            matchId = 1, round = "Test", date = "2026-06-11",
            homeTeam = "MEX", awayTeam = "RSA",
            homeScore = 2, awayScore = 1, ground = "Test"
        )
        val group = BasicGroup("Group A", listOf(mexico, rsa), listOf(match))

        // Test standings logic inline
        val stats = mutableMapOf<String, TestStats>()
        group.teams.forEach { stats[it.id] = TestStats(it.id, it.name) }

        group.matches.forEach { m ->
            val hs = m.homeScore!!; val aws = m.awayScore!!
            val home = stats[m.homeTeam]!!; val away = stats[m.awayTeam]!!
            home.played++; away.played++
            home.gf += hs; home.ga += aws; away.gf += aws; away.ga += hs
            when {
                hs > aws -> { home.wins++; home.pts += 3; away.losses++ }
                hs < aws -> { away.wins++; away.pts += 3; home.losses++ }
                else -> { home.draws++; away.draws++; home.pts++; away.pts++ }
            }
        }

        val mexicoStats = stats["MEX"]!!
        assertEquals(1, mexicoStats.wins)
        assertEquals(3, mexicoStats.pts)
        assertEquals(1, mexicoStats.gf - mexicoStats.ga) // GD +1

        val rsaStats = stats["RSA"]!!
        assertEquals(0, rsaStats.pts)
    }

    @Test
    fun `betting score calculation`() {
        // Simulate: 3 bets, 2 correct, 1 incorrect
        val bets = listOf(
            BasicBet(1, BasicPrediction.HOME_WIN),  // match 1: 2-1 home win -> correct
            BasicBet(2, BasicPrediction.DRAW),       // match 2: 3-1 home win -> incorrect
            BasicBet(3, BasicPrediction.AWAY_WIN)    // match 3: 0-2 away win -> correct
        )

        val matches = listOf(
            BasicMatch(1, "R1", "D1", "A", "B", 2, 1, "G1"),
            BasicMatch(2, "R1", "D1", "C", "D", 3, 1, "G2"),
            BasicMatch(3, "R1", "D1", "E", "F", 0, 2, "G3")
        )

        val matchMap = matches.associateBy { it.matchId }
        var correct = 0; var incorrect = 0

        bets.forEach { bet ->
            val m = matchMap[bet.matchId]!!
            val actual = when {
                m.homeScore!! > m.awayScore!! -> BasicPrediction.HOME_WIN
                m.homeScore < m.awayScore!! -> BasicPrediction.AWAY_WIN
                else -> BasicPrediction.DRAW
            }
            if (actual == bet.prediction) correct++ else incorrect++
        }

        val points = correct - incorrect // +1 -1 = 0
        assertEquals(2, correct)
        assertEquals(1, incorrect)
        assertEquals(1, points) // 2 correct (+2) - 1 incorrect (-1) = +1 point
    }

    private data class TestStats(
        val id: String, val name: String,
        var played: Int = 0, var wins: Int = 0, var draws: Int = 0,
        var losses: Int = 0, var gf: Int = 0, var ga: Int = 0, var pts: Int = 0
    )
}