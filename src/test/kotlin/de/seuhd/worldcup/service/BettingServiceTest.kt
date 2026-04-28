package de.seuhd.worldcup.service

import de.seuhd.worldcup.model.*
import kotlin.test.*

class BettingServiceTest {

    private fun makeMatch(id: Int, hs: Int?, aws: Int?) = Match(
        matchId = id, round = "Test", date = "2026-06-11",
        homeTeam = "A", awayTeam = "B", homeScore = hs, awayScore = aws,
        ground = "Test", odds = Odds(1.5, 3.5, 5.0)
    )

    @Test
    fun `place bet with money and calculate payout`() {
        val service = BettingService()
        val odds = Odds(2.0, 3.0, 4.0)
        service.placeBet(1, Prediction.HOME_WIN, 100.0, odds)

        val result = service.calculateMoneyResult(listOf(makeMatch(1, 2, 1)))
        assertEquals(100.0, result.totalWagered, 0.01)
        assertEquals(200.0, result.totalWon, 0.01)
        assertEquals(100.0, result.netProfit, 0.01)
        assertEquals(1, result.correct)
    }

    @Test
    fun `lost bet returns zero payout`() {
        val service = BettingService()
        val odds = Odds(2.0, 3.0, 4.0)
        service.placeBet(1, Prediction.HOME_WIN, 100.0, odds)

        val result = service.calculateMoneyResult(listOf(makeMatch(1, 0, 2)))
        assertEquals(100.0, result.totalWagered, 0.01)
        assertEquals(0.0, result.totalWon, 0.01)
        assertEquals(-100.0, result.netProfit, 0.01)
        assertEquals(1, result.incorrect)
    }

    @Test
    fun `pending bet does not count in decided`() {
        val service = BettingService()
        val odds = Odds(2.0, 3.0, 4.0)
        service.placeBet(1, Prediction.HOME_WIN, 100.0, odds)

        val result = service.calculateMoneyResult(listOf(makeMatch(1, null, null)))
        assertEquals(0, result.correct)
        assertEquals(0, result.incorrect)
        assertEquals(1, result.pending)
    }

    @Test
    fun `addOrUpdateBet replaces existing`() {
        val service = BettingService()
        val odds = Odds(2.0, 3.0, 4.0)
        service.placeBet(1, Prediction.HOME_WIN, 100.0, odds)
        service.placeBet(1, Prediction.DRAW, 50.0, odds)

        assertEquals(1, service.getBets().size)
        assertEquals(Prediction.DRAW, service.getBets()[0].prediction)
        assertEquals(50.0, service.getBets()[0].amount, 0.01)
    }

    @Test
    fun `calculateScoreForMatches filters correctly`() {
        val service = BettingService()
        val odds = Odds(2.0, 3.0, 4.0)
        service.placeBet(1, Prediction.HOME_WIN, 100.0, odds)
        service.placeBet(2, Prediction.AWAY_WIN, 50.0, odds)

        val result = service.calculateScoreForMatches(listOf(makeMatch(1, 2, 1)))
        assertEquals(1, result.total)
        assertEquals(1, result.correct)
    }
}