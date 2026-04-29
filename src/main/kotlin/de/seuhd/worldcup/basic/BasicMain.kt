package de.seuhd.worldcup.basic

private val userBets = mutableListOf<BasicBet>()

fun main() {
    println("Loading World Cup 2026 data...")
    val worldCup = BasicParser.load()
    println("Loaded: ${worldCup.tournament}")
    println("Found ${worldCup.groups.size} groups.")

    while (true) {
        println()
        println("===== FIFA World Cup 2026 - Betting Console =====")
        println("1) Show Standings")
        println("2) Show Matches")
        println("3) Place Bets")
        println("4) Show Betting Score")
        println("5) Exit")
        println("=================================================")
        print("Choose an option (1 to 5): ")

        when (readln().trim()) {
            "1" -> showStandings(worldCup.groups)
            "2" -> showMatches(worldCup.groups)
            "3" -> placeBets(worldCup.groups)
            "4" -> showBettingScore(worldCup.groups)
            "5" -> {
                println("Goodbye!")
                return
            }
            else -> println("Invalid option. Please enter 1-5.")
        }
    }
}

/* ----------------------------------------------------------------------------
   HELPERS
   ---------------------------------------------------------------------------- */
private fun findGroup(allGroups: List<BasicGroup>, input: String): BasicGroup? {
    val trimmed = input.trim()
    allGroups.find { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
    if (trimmed.length == 1) {
        val letter = trimmed.uppercase()
        allGroups.find { it.name.equals("Group $letter", ignoreCase = true) }?.let { return it }
    }
    val number = trimmed.toIntOrNull()
    if (number != null && number >= 1 && number <= allGroups.size) {
        val letter = ('A' + number - 1).toString()
        allGroups.find { it.name.equals("Group $letter", ignoreCase = true) }?.let { return it }
    }
    return null
}

private fun printAvailableGroups(allGroups: List<BasicGroup>) {
    println("Available groups:")
    allGroups.forEachIndexed { index, group ->
        val letter = ('A' + index).toString()
        println("  ${index + 1}. ${group.name} (type: ${index + 1}, $letter, or ${group.name})")
    }
}

/* ----------------------------------------------------------------------------
   1) SHOW STANDINGS
   ---------------------------------------------------------------------------- */
private fun showStandings(allGroups: List<BasicGroup>) {
    printAvailableGroups(allGroups)
    print("Enter group name (e.g., 'Group A') or type 'all': ")
    val input = readln().trim()

    if (input.lowercase() == "all") {
        allGroups.forEach { group ->
            printGroupStandings(group)
            println()
        }
    } else {
        val group = findGroup(allGroups, input)
        if (group != null) {
            printGroupStandings(group)
        } else {
            println("Group '$input' not found.")
        }
    }
}

private fun printGroupStandings(group: BasicGroup) {
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

    val standings = stats.values.map { s ->
        TeamStanding(
            teamId = s.teamId, teamName = s.teamName, played = s.played,
            wins = s.wins, draws = s.draws, losses = s.losses,
            goalsFor = s.goalsFor, goalsAgainst = s.goalsAgainst, points = s.points
        )
    }.sortedWith(
        compareByDescending<TeamStanding> { it.points }
            .thenByDescending { it.goalsFor - it.goalsAgainst }
    )

    println("\n--- ${group.name} Standings ---")
    println(String.format(
        "%-4s %-20s %2s %2s %2s %2s %3s %3s %3s %3s",
        "Pos", "Team", "P", "W", "D", "L", "GF", "GA", "GD", "Pts"
    ))
    standings.forEachIndexed { index, team ->
        println(String.format(
            "%-4d %-20s %2d %2d %2d %2d %3d %3d %3d %3d",
            index + 1, team.teamName, team.played, team.wins, team.draws,
            team.losses, team.goalsFor, team.goalsAgainst,
            team.goalsFor - team.goalsAgainst, team.points
        ))
    }
}

private data class MutableStats(
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

private data class TeamStanding(
    val teamId: String,
    val teamName: String,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int
)

/* ----------------------------------------------------------------------------
   2) SHOW MATCHES
   ---------------------------------------------------------------------------- */
private fun showMatches(allGroups: List<BasicGroup>) {
    printAvailableGroups(allGroups)
    print("Which group's matches do you want to see? ")
    val input = readln().trim()

    val group = findGroup(allGroups, input)
    if (group == null) {
        println("Group '$input' not found.")
        return
    }

    val teamNames = group.teams.associate { it.id to it.name }

    println("\n--- ${group.name} Matches ---")
    group.matches.forEach { match ->
        val homeName = teamNames[match.homeTeam] ?: match.homeTeam
        val awayName = teamNames[match.awayTeam] ?: match.awayTeam
        val scoreText = if (match.homeScore != null && match.awayScore != null) {
            "${match.homeScore} - ${match.awayScore}"
        } else {
            "vs"
        }
        println("${match.date}: $homeName $scoreText $awayName")
    }
}

/* ----------------------------------------------------------------------------
   3) PLACE BETS
   ---------------------------------------------------------------------------- */
private fun placeBets(allGroups: List<BasicGroup>) {
    printAvailableGroups(allGroups)
    print("Which group do you want to bet on? ")
    val input = readln().trim()

    val group = findGroup(allGroups, input)
    if (group == null) {
        println("Group '$input' not found.")
        return
    }

    val teamNames = group.teams.associate { it.id to it.name }

    println("\nPlacing bets for ${group.name}...")
    println("For each match, enter: 1=Home Win, 2=Away Win, 0=Draw")

    group.matches.forEach { match ->
        val homeName = teamNames[match.homeTeam] ?: match.homeTeam
        val awayName = teamNames[match.awayTeam] ?: match.awayTeam

        println("\nMatch: $homeName vs $awayName (${match.date})")
        print("Your prediction (1/2/0): ")

        val prediction = when (readln().trim()) {
            "1" -> BasicPrediction.HOME_WIN
            "2" -> BasicPrediction.AWAY_WIN
            "0" -> BasicPrediction.DRAW
            else -> {
                println("Invalid input. Skipping this match.")
                return@forEach
            }
        }

        // Remove existing bet for this match if any
        userBets.removeAll { it.matchId == match.matchId }
        userBets.add(BasicBet(matchId = match.matchId, prediction = prediction))
        println("Bet recorded!")
    }

    println("\nFinished placing bets for ${group.name}.")
}

/* ----------------------------------------------------------------------------
   4) SHOW BETTING SCORE  (+1 correct, -1 incorrect)
   ---------------------------------------------------------------------------- */
private fun showBettingScore(allGroups: List<BasicGroup>) {
    if (userBets.isEmpty()) {
        println("You haven't placed any bets yet.")
        return
    }

    val allMatches = allGroups.flatMap { it.matches }
    val matchMap = allMatches.associateBy { it.matchId }

    var correct = 0
    var incorrect = 0
    var pending = 0

    userBets.forEach { bet ->
        val match = matchMap[bet.matchId]
        if (match == null) return@forEach

        val homeScore = match.homeScore
        val awayScore = match.awayScore

        if (homeScore == null || awayScore == null) {
            pending++
            return@forEach
        }

        val actual = when {
            homeScore > awayScore -> BasicPrediction.HOME_WIN
            homeScore < awayScore -> BasicPrediction.AWAY_WIN
            else -> BasicPrediction.DRAW
        }

        if (actual == bet.prediction) correct++ else incorrect++
    }

    val points = correct - incorrect  // +1 per correct, -1 per incorrect
    val decided = correct + incorrect
    val percentage = if (decided > 0) (correct * 100) / decided else 0

    println("\n=== Your Betting Score ===")
    println("Correct predictions: $correct")
    println("Incorrect predictions: $incorrect")
    if (pending > 0) println("Pending matches: $pending")
    println("Total bets evaluated: $decided")
    println("Points: $points")  // +1 per correct, -1 per incorrect
    println("Success rate: $percentage%")
}