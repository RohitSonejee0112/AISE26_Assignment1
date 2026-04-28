package de.seuhd.worldcup.service

import de.seuhd.worldcup.model.Bet
import de.seuhd.worldcup.model.BetWithMoney
import de.seuhd.worldcup.model.Match
import de.seuhd.worldcup.model.Odds
import de.seuhd.worldcup.model.Prediction

class BettingService {
    private val bets = mutableListOf<BetWithMoney>()

    /** Phase 3: Place a bet with money */
    fun placeBet(matchId: Int, prediction: Prediction, amount: Double, odds: Odds): BetWithMoney {
        val oddsValue = when (prediction) {
            Prediction.HOME_WIN -> odds.homeWin
            Prediction.DRAW -> odds.draw
            Prediction.AWAY_WIN -> odds.awayWin
        }
        val bet = BetWithMoney(matchId, prediction, amount, oddsValue)
        val existingIndex = bets.indexOfFirst { it.matchId == matchId }
        if (existingIndex >= 0) {
            bets[existingIndex] = bet
        } else {
            bets.add(bet)
        }
        return bet
    }

    fun removeBet(matchId: Int): Boolean {
        return bets.removeAll { it.matchId == matchId }
    }

    fun getBetFor(matchId: Int): BetWithMoney? = bets.find { it.matchId == matchId }
    fun hasBetFor(matchId: Int): Boolean = bets.any { it.matchId == matchId }
    fun getBets(): List<BetWithMoney> = bets.toList()
    fun getBetsForMatches(matchIds: Set<Int>): List<BetWithMoney> = bets.filter { it.matchId in matchIds }

    fun clearBets() {
        bets.clear()
    }

    /** Calculate payout for a single bet */
    fun calculatePayout(bet: BetWithMoney, match: Match): Double {
        val hs = match.homeScore
        val aws = match.awayScore
        if (hs == null || aws == null) return 0.0

        val actual = when {
            hs > aws -> Prediction.HOME_WIN
            hs < aws -> Prediction.AWAY_WIN
            else -> Prediction.DRAW
        }

        return if (actual == bet.prediction) {
            bet.amount * bet.odds
        } else {
            0.0
        }
    }

    /** Calculate net result for a set of matches */
    fun calculateMoneyResult(matches: List<Match>): MoneyResult {
        val matchMap = matches.associateBy { it.matchId }
        val relevantBets = bets.filter { it.matchId in matchMap.keys }

        var totalWagered = 0.0
        var totalWon = 0.0
        var correct = 0
        var incorrect = 0
        var pending = 0

        relevantBets.forEach { bet ->
            val match = matchMap[bet.matchId] ?: return@forEach
            val hs = match.homeScore
            val aws = match.awayScore

            totalWagered += bet.amount

            if (hs == null || aws == null) {
                pending++
                return@forEach
            }

            val actual = when {
                hs > aws -> Prediction.HOME_WIN
                hs < aws -> Prediction.AWAY_WIN
                else -> Prediction.DRAW
            }

            if (actual == bet.prediction) {
                correct++
                totalWon += bet.amount * bet.odds
            } else {
                incorrect++
            }
        }

        return MoneyResult(
            totalWagered = totalWagered,
            totalWon = totalWon,
            netProfit = totalWon - totalWagered,
            correct = correct,
            incorrect = incorrect,
            pending = pending,
            totalBets = relevantBets.size
        )
    }

    /** Legacy: calculate score without money (for compatibility) */
    fun calculateScoreForMatches(matches: List<Match>): BettingResult {
        val matchMap = matches.associateBy { it.matchId }
        val relevantBets = bets.filter { it.matchId in matchMap.keys }
        var correct = 0
        var incorrect = 0
        var pending = 0

        relevantBets.forEach { bet ->
            val match = matchMap[bet.matchId] ?: return@forEach
            val hs = match.homeScore
            val aws = match.awayScore

            if (hs == null || aws == null) {
                pending++
                return@forEach
            }

            val actual = when {
                hs > aws -> Prediction.HOME_WIN
                hs < aws -> Prediction.AWAY_WIN
                else -> Prediction.DRAW
            }

            if (actual == bet.prediction) correct++ else incorrect++
        }

        return BettingResult(correct, incorrect, pending, relevantBets.size)
    }
}

data class BettingResult(
    val correct: Int,
    val incorrect: Int,
    val pending: Int,
    val total: Int
) {
    val decided: Int get() = correct + incorrect
    val successRate: Int get() = if (decided > 0) (correct * 100) / decided else 0
}

data class MoneyResult(
    val totalWagered: Double,
    val totalWon: Double,
    val netProfit: Double,
    val correct: Int,
    val incorrect: Int,
    val pending: Int,
    val totalBets: Int
) {
    val decided: Int get() = correct + incorrect
    val successRate: Int get() = if (decided > 0) (correct * 100) / decided else 0
}