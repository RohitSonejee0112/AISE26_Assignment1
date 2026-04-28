package de.seuhd.worldcup.service

import de.seuhd.worldcup.model.Group
import de.seuhd.worldcup.model.Knockout
import de.seuhd.worldcup.model.Match

/**
 * Tracks cumulative match records through tournament phases.
 * Group stage = 3 matches. Each knockout round adds 1 match for survivors.
 */
class CumulativeRecordService {

    data class CumulativeRecord(
        val teamId: String,
        val teamName: String,
        var played: Int = 0,
        var wins: Int = 0,
        var draws: Int = 0,
        var losses: Int = 0,
        var goalsFor: Int = 0,
        var goalsAgainst: Int = 0
    ) {
        val points: Int get() = wins * 3 + draws
        val goalDifference: Int get() = goalsFor - goalsAgainst
    }

    private val records = mutableMapOf<String, CumulativeRecord>()

    /** Initialize from group stage results */
    fun initFromGroups(groups: List<Group>) {
        records.clear()
        groups.forEach { group ->
            group.teams.forEach { team ->
                records[team.id] = CumulativeRecord(team.id, team.name)
            }
            group.matches.forEach { match ->
                processMatch(match)
            }
        }
    }

    /** Add a knockout round's results to cumulative records */
    fun addKnockoutRound(knockouts: List<Knockout>) {
        knockouts.forEach { k ->
            val match = Match(
                matchId = k.matchId,
                round = k.round,
                date = k.date,
                homeTeam = k.homeTeam ?: k.homePlaceholder,
                awayTeam = k.awayTeam ?: k.awayPlaceholder,
                homeScore = k.homeScore,
                awayScore = k.awayScore,
                ground = k.ground,
                odds = k.odds
            )
            processMatch(match)
        }
    }

    private fun processMatch(match: Match) {
        val hs = match.homeScore
        val aws = match.awayScore
        if (hs == null || aws == null) return

        val home = records[match.homeTeam]
        val away = records[match.awayTeam]
        if (home == null || away == null) return

        home.played++; away.played++
        home.goalsFor += hs; home.goalsAgainst += aws
        away.goalsFor += aws; away.goalsAgainst += hs

        when {
            hs > aws -> { home.wins++; away.losses++ }
            hs < aws -> { away.wins++; home.losses++ }
            else -> { home.draws++; away.draws++ }
        }
    }

    fun getRecord(teamId: String): CumulativeRecord? = records[teamId]

    fun formatRecord(teamId: String): String {
        val r = records[teamId] ?: return "N/A"
        return "${r.wins}W-${r.draws}D-${r.losses}L, ${r.points}pts, GD:${if (r.goalDifference >= 0) "+" else ""}${r.goalDifference} (${r.played} matches)"
    }
}