package de.seuhd.worldcup.basic

import kotlinx.serialization.Serializable

@Serializable
data class BasicWorldCupData(
    val tournament: String,
    val groups: List<BasicGroup>,
    val knockouts: List<BasicKnockout>
)

@Serializable
data class BasicGroup(
    val name: String,
    val teams: List<BasicTeam>,
    val matches: List<BasicMatch>
)

@Serializable
data class BasicTeam(
    val id: String,
    val name: String
)

@Serializable
data class BasicMatch(
    val matchId: Int,
    val round: String,
    val date: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val ground: String
)

@Serializable
data class BasicKnockout(
    val matchId: Int,
    val round: String,
    val date: String,
    val homePlaceholder: String,
    val awayPlaceholder: String,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val ground: String
)

enum class BasicPrediction {
    HOME_WIN, AWAY_WIN, DRAW
}

data class BasicBet(
    val matchId: Int,
    val prediction: BasicPrediction
)