package de.seuhd.worldcup.service

import de.seuhd.worldcup.model.Group
import de.seuhd.worldcup.model.Match

data class TeamStanding(
    val teamId: String,
    val teamName: String,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

private class MutableStats(
    val teamId: String,
    val teamName: String,
    var played: Int = 0,
    var wins: Int = 0,
    var draws: Int = 0,
    var losses: Int = 0,
    var goalsFor: Int = 0,
    var goalsAgainst: Int = 0,
    var points: Int = 0
)

class StandingsService {

    /** Calculate actual standings from match results */
    fun calculateStandings(group: Group): List<TeamStanding> {
        val stats = mutableMapOf<String, MutableStats>()
        group.teams.forEach { team ->
            stats[team.id] = MutableStats(team.id, team.name)
        }

        group.matches.forEach { match ->
            val homeScore = match.homeScore
            val awayScore = match.awayScore
            if (homeScore != null && awayScore != null) {
                val home = stats[match.homeTeam]!!
                val away = stats[match.awayTeam]!!
                home.played++; away.played++
                home.goalsFor += homeScore; home.goalsAgainst += awayScore
                away.goalsFor += awayScore; away.goalsAgainst += homeScore

                when {
                    homeScore > awayScore -> {
                        home.wins++; home.points += 3
                        away.losses++
                    }
                    homeScore < awayScore -> {
                        away.wins++; away.points += 3
                        home.losses++
                    }
                    else -> {
                        home.draws++; away.draws++
                        home.points++; away.points++
                    }
                }
            }
        }

        return stats.values.map { s ->
            TeamStanding(
                teamId = s.teamId, teamName = s.teamName, played = s.played,
                wins = s.wins, draws = s.draws, losses = s.losses,
                goalsFor = s.goalsFor, goalsAgainst = s.goalsAgainst, points = s.points
            )
        }.sortedWith(
            compareByDescending<TeamStanding> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
        )
    }

    /** All-zero standings for pre-tournament view */
    fun calculateEmptyStandings(group: Group): List<TeamStanding> {
        return group.teams.map { team ->
            TeamStanding(team.id, team.name, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    /** Format a team's record as "3W-0D-0L, 9pts, GD:+4" */
    fun formatRecord(standing: TeamStanding): String {
        return "${standing.wins}W-${standing.draws}D-${standing.losses}L, ${standing.points}pts, GD:${if (standing.goalDifference >= 0) "+" else ""}${standing.goalDifference}"
    }
}