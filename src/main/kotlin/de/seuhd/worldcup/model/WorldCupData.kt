package de.seuhd.worldcup.model

import kotlinx.serialization.Serializable

@Serializable
data class WorldCupData(
    val tournament: String,
    val groups: List<Group>,
    val knockouts: List<Knockout>
)

@Serializable
data class Group(
    val name: String,
    val teams: List<Team>,
    val matches: List<Match>
)

@Serializable
data class Team(
    val id: String,
    val name: String
)

@Serializable
data class Odds(
    val homeWin: Double,
    val draw: Double,
    val awayWin: Double
)

@Serializable
data class Match(
    val matchId: Int,
    val round: String,
    val date: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val ground: String,
    val odds: Odds
)

@Serializable
data class Knockout(
    val matchId: Int,
    val round: String,
    val date: String,
    val homePlaceholder: String,
    val awayPlaceholder: String,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val ground: String,
    val odds: Odds
)

enum class Prediction {
    HOME_WIN, AWAY_WIN, DRAW
}

data class Bet(
    val matchId: Int,
    val prediction: Prediction
)

/** Phase 3: Bet with money amount */
data class BetWithMoney(
    val matchId: Int,
    val prediction: Prediction,
    val amount: Double,
    val odds: Double  // The odds at time of betting
)