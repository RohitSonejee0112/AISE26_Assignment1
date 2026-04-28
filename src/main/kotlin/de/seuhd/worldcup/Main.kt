package de.seuhd.worldcup

import de.seuhd.worldcup.model.*
import de.seuhd.worldcup.parser.JsonParser
import de.seuhd.worldcup.service.BettingResult
import de.seuhd.worldcup.service.BettingService
import de.seuhd.worldcup.service.CumulativeRecordService
import de.seuhd.worldcup.service.MoneyResult
import de.seuhd.worldcup.service.StandingsService
import de.seuhd.worldcup.service.TeamStanding

/* ============================================================================
   MAIN
   ============================================================================ */
fun main() {
    val worldCup = JsonParser.load("world_cup_2026_filled_data.json")
    val standingsService = StandingsService()
    val bettingService = BettingService()
    val cumulativeService = CumulativeRecordService()

    val teamNameMap = worldCup.groups.flatMap { it.teams }.associate { it.id to it.name }
    val allGroupMatches = worldCup.groups.flatMap { it.matches }

    // Precompute actual group standings
    val actualGroupStandings = worldCup.groups.associate { group ->
        group.name to standingsService.calculateStandings(group)
    }
    val teamStandingMap = actualGroupStandings.values.flatten().associate { it.teamId to it }

    // Initialize cumulative records with group stage
    cumulativeService.initFromGroups(worldCup.groups)

    var phase: TournamentPhase = TournamentPhase.BEFORE_GROUP_STAGE

    while (true) {
        phase = when (phase) {
            TournamentPhase.BEFORE_GROUP_STAGE ->
                beforeGroupStage(worldCup, standingsService, bettingService, teamNameMap)

            TournamentPhase.GROUP_STAGE_OVER ->
                groupStageOver(worldCup, bettingService, standingsService, allGroupMatches, actualGroupStandings, teamNameMap)

            TournamentPhase.BEFORE_ROUND_OF_32 ->
                beforeKnockoutRound(worldCup, bettingService, cumulativeService, teamNameMap,
                    "BEFORE ROUND OF 32", "Round of 32", TournamentPhase.ROUND_OF_32_OVER)

            TournamentPhase.ROUND_OF_32_OVER -> {
                // Add Ro32 results to cumulative records
                val ro32 = worldCup.knockouts.filter { it.round == "Round of 32" }
                cumulativeService.addKnockoutRound(ro32)
                knockoutRoundOver(worldCup, bettingService, cumulativeService, teamNameMap,
                    "ROUND OF 32 OVER", "Round of 32", TournamentPhase.BEFORE_ROUND_OF_16)
            }

            TournamentPhase.BEFORE_ROUND_OF_16 ->
                beforeKnockoutRound(worldCup, bettingService, cumulativeService, teamNameMap,
                    "BEFORE ROUND OF 16", "Round of 16", TournamentPhase.ROUND_OF_16_OVER)

            TournamentPhase.ROUND_OF_16_OVER -> {
                val ro16 = worldCup.knockouts.filter { it.round == "Round of 16" }
                cumulativeService.addKnockoutRound(ro16)
                knockoutRoundOver(worldCup, bettingService, cumulativeService, teamNameMap,
                    "ROUND OF 16 OVER", "Round of 16", TournamentPhase.BEFORE_QUARTER_FINAL)
            }

            TournamentPhase.BEFORE_QUARTER_FINAL ->
                beforeKnockoutDirect(worldCup, bettingService, cumulativeService, teamNameMap,
                    "BEFORE QUARTER-FINALS", "Quarter-final", TournamentPhase.QUARTER_FINAL_OVER)

            TournamentPhase.QUARTER_FINAL_OVER -> {
                val qf = worldCup.knockouts.filter { it.round == "Quarter-final" }
                cumulativeService.addKnockoutRound(qf)
                knockoutRoundOver(worldCup, bettingService, cumulativeService, teamNameMap,
                    "QUARTER-FINALS OVER", "Quarter-final", TournamentPhase.BEFORE_SEMI_FINAL)
            }

            TournamentPhase.BEFORE_SEMI_FINAL ->
                beforeKnockoutDirect(worldCup, bettingService, cumulativeService, teamNameMap,
                    "BEFORE SEMI-FINALS", "Semi-final", TournamentPhase.SEMI_FINAL_OVER)

            TournamentPhase.SEMI_FINAL_OVER -> {
                val sf = worldCup.knockouts.filter { it.round == "Semi-final" }
                cumulativeService.addKnockoutRound(sf)
                knockoutRoundOver(worldCup, bettingService, cumulativeService, teamNameMap,
                    "SEMI-FINALS OVER", "Semi-final", TournamentPhase.BEFORE_FINAL)
            }

            TournamentPhase.BEFORE_FINAL ->
                beforeKnockoutDirect(worldCup, bettingService, cumulativeService, teamNameMap,
                    "BEFORE FINAL", "Final", TournamentPhase.TOURNAMENT_OVER)

            TournamentPhase.TOURNAMENT_OVER -> {
                val third = worldCup.knockouts.filter { it.round == "Match for third place" }
                cumulativeService.addKnockoutRound(third)
                val final = worldCup.knockouts.filter { it.round == "Final" }
                cumulativeService.addKnockoutRound(final)
                tournamentOver(worldCup, bettingService, cumulativeService, teamNameMap)
            }
        }
    }
}

/* ============================================================================
   HELPERS
   ============================================================================ */
private fun formatPrediction(p: Prediction?): String = when (p) {
    Prediction.HOME_WIN -> "1 (Home Win)"
    Prediction.AWAY_WIN -> "2 (Away Win)"
    Prediction.DRAW -> "0 (Draw)"
    null -> "None"
}

private fun printSeparator() = println("=".repeat(55))

private fun findGroup(allGroups: List<Group>, input: String): Group? {
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

private fun printAvailableGroups(allGroups: List<Group>) {
    println("Choose by number (1-${allGroups.size}) or letter (A-${'A' + allGroups.size - 1})")
    println()
    val cols = 3
    val colWidth = 24
    for (rowStart in allGroups.indices step cols) {
        val rowGroups = allGroups.subList(rowStart, minOf(rowStart + cols, allGroups.size))
        val headers = rowGroups.mapIndexed { i, group ->
            val globalIdx = rowStart + i
            val num = globalIdx + 1
            val letter = ('A' + globalIdx)
            "[${num}/${letter}] ${group.name}".padEnd(colWidth)
        }
        println(headers.joinToString("   "))
        val maxTeams = rowGroups.maxOf { it.teams.size }
        for (t in 0 until maxTeams) {
            val lines = rowGroups.map { group ->
                if (t < group.teams.size) "   ${group.teams[t].name}".padEnd(colWidth) else "".padEnd(colWidth)
            }
            println(lines.joinToString("   "))
        }
        println()
    }
}

/* ============================================================================
   PHASE 3: MONEY BETTING PROMPT
   ============================================================================ */
private fun promptForMoneyBet(
    matchId: Int,
    bettingService: BettingService,
    odds: Odds
): Boolean {
    println("\nEnter prediction: 1=Home Win, 2=Away Win, 0=Draw, x=Remove bet")
    val predInput = readln().trim()

    if (predInput == "x" || predInput == "X") {
        if (bettingService.removeBet(matchId)) println("Bet removed.") else println("No bet to remove.")
        return true
    }

    val prediction = when (predInput) {
        "1" -> Prediction.HOME_WIN
        "2" -> Prediction.AWAY_WIN
        "0" -> Prediction.DRAW
        else -> {
            println("Invalid input. No changes made.")
            return false
        }
    }

    val selectedOdds = when (prediction) {
        Prediction.HOME_WIN -> odds.homeWin
        Prediction.DRAW -> odds.draw
        Prediction.AWAY_WIN -> odds.awayWin
    }

    println("Selected: ${formatPrediction(prediction)} @ odds $selectedOdds")
    print("Enter bet amount ($): ")

    val amount = readln().trim().toDoubleOrNull()
    if (amount == null || amount <= 0) {
        println("Invalid amount. No bet placed.")
        return false
    }

    val potentialWin = amount * selectedOdds
    println("\nPotential win: $${String.format("%.2f", potentialWin)}")
    println("1) Confirm")
    println("2) Edit")
    print("Choose: ")

    when (readln().trim()) {
        "1" -> {
            bettingService.placeBet(matchId, prediction, amount, odds)
            println("Bet confirmed: $$amount on ${formatPrediction(prediction)} @ $selectedOdds")
            return true
        }
        "2" -> {
            println("Edit cancelled. Returning to match selection.")
            return false
        }
        else -> {
            println("Invalid choice. Bet cancelled.")
            return false
        }
    }
}

/* ============================================================================
   VIEW / MODIFY BETS (Phase 3 with money)
   ============================================================================ */
private fun viewAndModifyBets(
    allMatches: List<Match>,
    allKnockouts: List<Knockout>,
    bettingService: BettingService,
    teamNameMap: Map<String, String>
) {
    if (bettingService.getBets().isEmpty()) {
        println("You haven't placed any bets yet.")
        return
    }

    val matchMap = allMatches.associateBy { it.matchId }
    val knockoutMap = allKnockouts.associateBy { it.matchId }

    while (true) {
        val bets = bettingService.getBets()
        println("\n=== Your Current Bets ===")
        println("0. Back")

        bets.forEachIndexed { index, bet ->
            val num = index + 1
            val match = matchMap[bet.matchId]
            val knockout = knockoutMap[bet.matchId]

            val desc = when {
                match != null -> {
                    val home = teamNameMap[match.homeTeam] ?: match.homeTeam
                    val away = teamNameMap[match.awayTeam] ?: match.awayTeam
                    "$home vs $away (${match.date})"
                }
                knockout != null -> {
                    val home = teamNameMap[knockout.homeTeam] ?: knockout.homeTeam ?: knockout.homePlaceholder
                    val away = teamNameMap[knockout.awayTeam] ?: knockout.awayTeam ?: knockout.awayPlaceholder
                    "$home vs $away (${knockout.date})"
                }
                else -> "[Unknown match ${bet.matchId}]"
            }

            println("$num. $desc → ${formatPrediction(bet.prediction)} @ ${bet.odds} | $$${String.format("%.2f", bet.amount)}")
        }

        print("\nBet to modify? (0 = back): ")
        val choice = readln().trim().toIntOrNull()
        if (choice == null || choice < 0 || choice > bets.size) {
            println("Invalid choice.")
            continue
        }
        if (choice == 0) return

        val selectedBet = bets[choice - 1]
        val match = matchMap[selectedBet.matchId]
        val knockout = knockoutMap[selectedBet.matchId]

        println("\nSelected bet:")
        val odds = when {
            match != null -> match.odds
            knockout != null -> knockout.odds
            else -> return
        }

        println("Current: ${formatPrediction(selectedBet.prediction)} @ ${selectedBet.odds} | $$${String.format("%.2f", selectedBet.amount)}")
        println("Enter new prediction or x to remove:")

        promptForMoneyBet(selectedBet.matchId, bettingService, odds)
    }
}

/* ============================================================================
   PHASE: BEFORE GROUP STAGE
   ============================================================================ */
private fun beforeGroupStage(
    worldCup: WorldCupData,
    standingsService: StandingsService,
    bettingService: BettingService,
    teamNameMap: Map<String, String>
): TournamentPhase {
    while (true) {
        println()
        printSeparator()
        println("BEFORE GROUP STAGE")
        printSeparator()
        println("1) Show Standings (Pre-Tournament)")
        println("2) Show Matches")
        println("3) Place Bets")
        println("4) View / Modify Bets")
        println("5) Proceed to Group Stage")
        println("6) Exit")
        printSeparator()
        print("Choose: ")

        when (readln().trim()) {
            "1" -> showAllGroupStandings(worldCup.groups, standingsService, empty = true)
            "2" -> showGroupMatchesMenu(worldCup.groups, teamNameMap)
            "3" -> placeGroupBets(worldCup.groups, bettingService, teamNameMap)
            "4" -> viewAndModifyBets(
                worldCup.groups.flatMap { it.matches },
                worldCup.knockouts,
                bettingService,
                teamNameMap
            )
            "5" -> return TournamentPhase.GROUP_STAGE_OVER
            "6" -> {
                println("Goodbye!"); kotlin.system.exitProcess(0)
            }
            else -> println("Invalid option.")
        }
    }
}

private fun showAllGroupStandings(
    allGroups: List<Group>,
    standingsService: StandingsService,
    empty: Boolean
) {
    print("\nShow all groups or one? ('all' or group name/number): ")
    val input = readln().trim()

    if (input.lowercase() == "all") {
        allGroups.forEach { group ->
            if (empty) printEmptyStandings(group, standingsService)
            else printGroupStandings(group, standingsService)
            println()
        }
    } else {
        val group = findGroup(allGroups, input)
        if (group != null) {
            if (empty) printEmptyStandings(group, standingsService)
            else printGroupStandings(group, standingsService)
        } else {
            println("Group not found.")
        }
    }
}

private fun printEmptyStandings(group: Group, service: StandingsService) {
    val standings = service.calculateEmptyStandings(group)
    println("\n--- ${group.name} Standings ---")
    println(String.format("%-4s %-20s %2s %2s %2s %2s %3s %3s %3s %3s",
        "Pos", "Team", "P", "W", "D", "L", "GF", "GA", "GD", "Pts"))
    standings.forEachIndexed { index, team ->
        println(String.format("%-4d %-20s %2d %2d %2d %2d %3d %3d %3d %3d",
            index + 1, team.teamName, 0, 0, 0, 0, 0, 0, 0, 0))
    }
}

private fun printGroupStandings(group: Group, service: StandingsService) {
    val standings = service.calculateStandings(group)
    println("\n--- ${group.name} Standings ---")
    println(String.format("%-4s %-20s %2s %2s %2s %2s %3s %3s %3s %3s",
        "Pos", "Team", "P", "W", "D", "L", "GF", "GA", "GD", "Pts"))
    standings.forEachIndexed { index, team ->
        println(String.format("%-4d %-20s %2d %2d %2d %2d %3d %3d %3d %3d",
            index + 1, team.teamName, team.played, team.wins, team.draws,
            team.losses, team.goalsFor, team.goalsAgainst, team.goalDifference, team.points))
    }
}

private fun showGroupMatchesMenu(allGroups: List<Group>, teamNameMap: Map<String, String>) {
    printAvailableGroups(allGroups)
    print("Group? ")
    val input = readln().trim()

    val group = findGroup(allGroups, input)
    if (group == null) {
        println("Group not found.")
        return
    }

    println("\n--- ${group.name} Matches ---")
    group.matches.forEachIndexed { index, match ->
        val num = index + 1
        val homeName = teamNameMap[match.homeTeam] ?: match.homeTeam
        val awayName = teamNameMap[match.awayTeam] ?: match.awayTeam
        println("$num. ${match.date}: $homeName vs $awayName [${match.ground}]")
    }
}

private fun placeGroupBets(
    allGroups: List<Group>,
    bettingService: BettingService,
    teamNameMap: Map<String, String>
) {
    while (true) {
        printAvailableGroups(allGroups)
        print("Group? (0 = back): ")
        val input = readln().trim()
        if (input == "0") return

        val group = findGroup(allGroups, input)
        if (group == null) {
            println("Group not found.")
            continue
        }

        betOnGroupMatches(group, bettingService, teamNameMap)
    }
}

private fun betOnGroupMatches(
    group: Group,
    bettingService: BettingService,
    teamNameMap: Map<String, String>
) {
    while (true) {
        println("\n--- ${group.name} Matches ---")
        println("0. Back to group selection")

        group.matches.forEachIndexed { index, match ->
            val num = index + 1
            val homeName = teamNameMap[match.homeTeam] ?: match.homeTeam
            val awayName = teamNameMap[match.awayTeam] ?: match.awayTeam
            val existingBet = bettingService.getBetFor(match.matchId)
            val betText = if (existingBet != null)
                "[Bet: ${formatPrediction(existingBet.prediction)} $$${String.format("%.2f", existingBet.amount)}]"
            else
                "[No bet]"
            println("$num. $homeName vs $awayName (${match.date}) $betText")
            println("    Venue: ${match.ground}")
            println("    Odds: 1=${match.odds.homeWin} | 0=${match.odds.draw} | 2=${match.odds.awayWin}")
        }

        print("\nMatch? (0 = back): ")
        val choice = readln().trim().toIntOrNull()
        if (choice == null || choice < 0 || choice > group.matches.size) {
            println("Invalid choice.")
            continue
        }
        if (choice == 0) return

        val match = group.matches[choice - 1]
        val homeName = teamNameMap[match.homeTeam] ?: match.homeTeam
        val awayName = teamNameMap[match.awayTeam] ?: match.awayTeam

        println("\nSelected: $homeName vs $awayName")
        println("Venue: ${match.ground}")
        println("Odds: 1 (Home) ${match.odds.homeWin} | 0 (Draw) ${match.odds.draw} | 2 (Away) ${match.odds.awayWin}")

        val existing = bettingService.getBetFor(match.matchId)
        if (existing != null) {
            println("Current bet: ${formatPrediction(existing.prediction)} @ ${existing.odds} | $$${String.format("%.2f", existing.amount)}")
        }

        promptForMoneyBet(match.matchId, bettingService, match.odds)
    }
}

/* ============================================================================
   PHASE: GROUP STAGE OVER
   ============================================================================ */
private fun groupStageOver(
    worldCup: WorldCupData,
    bettingService: BettingService,
    standingsService: StandingsService,
    allGroupMatches: List<Match>,
    actualGroupStandings: Map<String, List<TeamStanding>>,
    teamNameMap: Map<String, String>
): TournamentPhase {
    while (true) {
        println()
        printSeparator()
        println("GROUP STAGE OVER")
        printSeparator()
        println("1) Group Standings")
        println("2) Bets Placed")
        println("3) Proceed to Round of 32")
        println("4) Exit")
        printSeparator()
        print("Choose: ")

        when (readln().trim()) {
            "1" -> {
                worldCup.groups.forEach { group ->
                    printGroupStandings(group, standingsService)
                    println()
                }
            }
            "2" -> showMoneyResults(allGroupMatches, bettingService, teamNameMap, "Group Stage")
            "3" -> return TournamentPhase.BEFORE_ROUND_OF_32
            "4" -> {
                println("Goodbye!"); kotlin.system.exitProcess(0)
            }
            else -> println("Invalid option.")
        }
    }
}

/* ============================================================================
   KNOCKOUT: Ro32, Ro16 (with submenu)
   ============================================================================ */
private fun beforeKnockoutRound(
    worldCup: WorldCupData,
    bettingService: BettingService,
    cumulativeService: CumulativeRecordService,
    teamNameMap: Map<String, String>,
    title: String,
    roundName: String,
    nextPhase: TournamentPhase
): TournamentPhase {
    val knockouts = worldCup.knockouts.filter { it.round == roundName }

    while (true) {
        println()
        printSeparator()
        println(title)
        printSeparator()
        println("1) Show Matches")
        println("2) Place Bets")
        println("3) View / Modify Bets")
        println("4) Proceed")
        println("5) Exit")
        printSeparator()
        print("Choose: ")

        when (readln().trim()) {
            "1" -> showKnockoutMatches(knockouts, teamNameMap, cumulativeService)
            "2" -> placeKnockoutBets(knockouts, bettingService, teamNameMap, cumulativeService)
            "3" -> viewAndModifyBets(
                worldCup.groups.flatMap { it.matches },
                worldCup.knockouts,
                bettingService,
                teamNameMap
            )
            "4" -> return nextPhase
            "5" -> {
                println("Goodbye!"); kotlin.system.exitProcess(0)
            }
            else -> println("Invalid option.")
        }
    }
}

private fun showKnockoutMatches(
    knockouts: List<Knockout>,
    teamNameMap: Map<String, String>,
    cumulativeService: CumulativeRecordService
) {
    println("\n--- Matches ---")
    knockouts.forEachIndexed { index, k ->
        val num = index + 1
        val home = teamNameMap[k.homeTeam] ?: k.homeTeam ?: k.homePlaceholder
        val away = teamNameMap[k.awayTeam] ?: k.awayTeam ?: k.awayPlaceholder
        println("$num. ${k.date}: $home vs $away [${k.ground}]")

        k.homeTeam?.let { cumulativeService.getRecord(it) }?.let {
            println("    $home: ${cumulativeService.formatRecord(it.teamId)}")
        }
        k.awayTeam?.let { cumulativeService.getRecord(it) }?.let {
            println("    $away: ${cumulativeService.formatRecord(it.teamId)}")
        }
    }
}

private fun placeKnockoutBets(
    knockouts: List<Knockout>,
    bettingService: BettingService,
    teamNameMap: Map<String, String>,
    cumulativeService: CumulativeRecordService
) {
    while (true) {
        println("\n--- Place Bets ---")
        println("0. Back")

        knockouts.forEachIndexed { index, k ->
            val num = index + 1
            val home = teamNameMap[k.homeTeam] ?: k.homeTeam ?: k.homePlaceholder
            val away = teamNameMap[k.awayTeam] ?: k.awayTeam ?: k.awayPlaceholder
            val existingBet = bettingService.getBetFor(k.matchId)
            val betText = if (existingBet != null)
                "[Bet: ${formatPrediction(existingBet.prediction)} $$${String.format("%.2f", existingBet.amount)}]"
            else
                "[No bet]"
            println("$num. $home vs $away (${k.date}) $betText")
        }

        print("\nMatch? (0 = back): ")
        val choice = readln().trim().toIntOrNull()
        if (choice == null || choice < 0 || choice > knockouts.size) {
            println("Invalid choice.")
            continue
        }
        if (choice == 0) return

        val k = knockouts[choice - 1]
        val home = teamNameMap[k.homeTeam] ?: k.homeTeam ?: k.homePlaceholder
        val away = teamNameMap[k.awayTeam] ?: k.awayTeam ?: k.awayPlaceholder

        println("\nSelected: $home vs $away")
        println("Venue: ${k.ground} | Date: ${k.date}")

        k.homeTeam?.let { cumulativeService.getRecord(it) }?.let {
            println("$home: ${cumulativeService.formatRecord(it.teamId)}")
        }
        k.awayTeam?.let { cumulativeService.getRecord(it) }?.let {
            println("$away: ${cumulativeService.formatRecord(it.teamId)}")
        }

        println("Odds: 1 (Home) ${k.odds.homeWin} | 0 (Draw) ${k.odds.draw} | 2 (Away) ${k.odds.awayWin}")

        val existing = bettingService.getBetFor(k.matchId)
        if (existing != null) {
            println("Current bet: ${formatPrediction(existing.prediction)} @ ${existing.odds} | $$${String.format("%.2f", existing.amount)}")
        }

        promptForMoneyBet(k.matchId, bettingService, k.odds)
    }
}

/* ============================================================================
   KNOCKOUT: QF, SF, Final (direct display)
   ============================================================================ */
private fun beforeKnockoutDirect(
    worldCup: WorldCupData,
    bettingService: BettingService,
    cumulativeService: CumulativeRecordService,
    teamNameMap: Map<String, String>,
    title: String,
    roundName: String,
    nextPhase: TournamentPhase
): TournamentPhase {
    val knockouts = worldCup.knockouts.filter { it.round == roundName }

    while (true) {
        println()
        printSeparator()
        println(title)
        printSeparator()

        knockouts.forEachIndexed { index, k ->
            val num = index + 1
            val home = teamNameMap[k.homeTeam] ?: k.homeTeam ?: k.homePlaceholder
            val away = teamNameMap[k.awayTeam] ?: k.awayTeam ?: k.awayPlaceholder
            val existingBet = bettingService.getBetFor(k.matchId)
            val betText = if (existingBet != null)
                "[Bet: ${formatPrediction(existingBet.prediction)} $$${String.format("%.2f", existingBet.amount)}]"
            else
                "[No bet]"

            println("\n$num. $home vs $away")
            println("   Venue: ${k.ground} | Date: ${k.date}")

            k.homeTeam?.let { cumulativeService.getRecord(it) }?.let {
                println("   $home: ${cumulativeService.formatRecord(it.teamId)}")
            }
            k.awayTeam?.let { cumulativeService.getRecord(it) }?.let {
                println("   $away: ${cumulativeService.formatRecord(it.teamId)}")
            }

            println("   Odds: 1=${k.odds.homeWin} | 0=${k.odds.draw} | 2=${k.odds.awayWin} $betText")
            println("   Enter b$num to bet on this match")
        }

        println("\n0. Proceed")
        println("x. Exit")
        print("Choose: ")

        val input = readln().trim()
        if (input == "0") return nextPhase
        if (input.lowercase() == "x") {
            println("Goodbye!"); kotlin.system.exitProcess(0)
        }

        if (input.startsWith("b", ignoreCase = true)) {
            val matchNum = input.drop(1).toIntOrNull()
            if (matchNum != null && matchNum in 1..knockouts.size) {
                val k = knockouts[matchNum - 1]
                println("\nBetting on: ${teamNameMap[k.homeTeam] ?: k.homeTeam} vs ${teamNameMap[k.awayTeam] ?: k.awayTeam}")
                val existing = bettingService.getBetFor(k.matchId)
                if (existing != null) {
                    println("Current bet: ${formatPrediction(existing.prediction)} @ ${existing.odds} | $$${String.format("%.2f", existing.amount)}")
                }
                promptForMoneyBet(k.matchId, bettingService, k.odds)
            } else {
                println("Invalid match number.")
            }
        } else {
            println("Invalid option.")
        }
    }
}

/* ============================================================================
   KNOCKOUT "OVER" SCREENS
   ============================================================================ */
private fun knockoutRoundOver(
    worldCup: WorldCupData,
    bettingService: BettingService,
    cumulativeService: CumulativeRecordService,
    teamNameMap: Map<String, String>,
    title: String,
    roundName: String,
    nextPhase: TournamentPhase
): TournamentPhase {
    val knockouts = worldCup.knockouts.filter { it.round == roundName }
    val knockoutMatches = knockouts.map { it.toMatch() }

    while (true) {
        println()
        printSeparator()
        println(title)
        printSeparator()
        println("1) Match Results")
        println("2) Bets Placed")
        println("3) Proceed")
        println("4) Exit")
        printSeparator()
        print("Choose: ")

        when (readln().trim()) {
            "1" -> {
                println("\n--- $roundName Results ---")
                knockouts.forEach { k ->
                    val home = teamNameMap[k.homeTeam] ?: k.homeTeam ?: k.homePlaceholder
                    val away = teamNameMap[k.awayTeam] ?: k.awayTeam ?: k.awayPlaceholder
                    val winner = if (k.homeScore!! > k.awayScore!!) home else away
                    println("${k.date}: $home ${k.homeScore}-${k.awayScore} $away → $winner")

                    k.homeTeam?.let { cumulativeService.getRecord(it) }?.let {
                        println("    $home: ${cumulativeService.formatRecord(it.teamId)}")
                    }
                    k.awayTeam?.let { cumulativeService.getRecord(it) }?.let {
                        println("    $away: ${cumulativeService.formatRecord(it.teamId)}")
                    }
                }
            }
            "2" -> showMoneyResults(knockoutMatches, bettingService, teamNameMap, roundName)
            "3" -> return nextPhase
            "4" -> {
                println("Goodbye!"); kotlin.system.exitProcess(0)
            }
            else -> println("Invalid option.")
        }
    }
}

/* ============================================================================
   TOURNAMENT OVER
   ============================================================================ */
private fun tournamentOver(
    worldCup: WorldCupData,
    bettingService: BettingService,
    cumulativeService: CumulativeRecordService,
    teamNameMap: Map<String, String>
): TournamentPhase {
    val allMatches = worldCup.groups.flatMap { it.matches } + worldCup.knockouts.map { it.toMatch() }

    println()
    printSeparator()
    println("TOURNAMENT OVER")
    printSeparator()

    val final = worldCup.knockouts.find { it.round == "Final" }!!
    val champion = if (final.homeScore!! > final.awayScore!!) {
        teamNameMap[final.homeTeam] ?: final.homeTeam
    } else {
        teamNameMap[final.awayTeam] ?: final.awayTeam
    }
    println("\n🏆 CHAMPION: $champion 🏆")
    println("Final: ${teamNameMap[final.homeTeam] ?: final.homeTeam} ${final.homeScore}-${final.awayScore} ${teamNameMap[final.awayTeam] ?: final.awayTeam}")

    // Show champion's full tournament record
    final.homeTeam?.let { cumulativeService.getRecord(it) }?.let {
        println("\n${teamNameMap[it.teamId]} full tournament: ${cumulativeService.formatRecord(it.teamId)}")
    }
    final.awayTeam?.let { cumulativeService.getRecord(it) }?.let {
        println("${teamNameMap[it.teamId]} full tournament: ${cumulativeService.formatRecord(it.teamId)}")
    }

    showMoneyResults(allMatches, bettingService, teamNameMap, "Tournament")

    println("\nThank you for playing!")
    kotlin.system.exitProcess(0)
}

/* ============================================================================
   SHARED: SHOW MONEY RESULTS (Phase 3)
   ============================================================================ */
private fun showMoneyResults(
    matches: List<Match>,
    bettingService: BettingService,
    teamNameMap: Map<String, String>,
    label: String
) {
    val result = bettingService.calculateMoneyResult(matches)
    val matchMap = matches.associateBy { it.matchId }

    println("\n=== $label Bet Results ===")
    println("Total wagered: $${String.format("%.2f", result.totalWagered)}")
    println("Total won: $${String.format("%.2f", result.totalWon)}")
    val profitColor = if (result.netProfit >= 0) "PROFIT" else "LOSS"
    println("Net $profitColor: $${String.format("%.2f", kotlin.math.abs(result.netProfit))}")
    println("Correct: ${result.correct} | Incorrect: ${result.incorrect}")
    if (result.pending > 0) println("Pending: ${result.pending}")
    println("Total bets: ${result.totalBets}")
    if (result.decided > 0) println("Success rate: ${result.successRate}%")

    val bets = bettingService.getBetsForMatches(matches.map { it.matchId }.toSet())
    if (bets.isEmpty()) {
        println("No bets placed for this phase.")
        return
    }

    println("\n--- Detailed Results ---")
    bets.forEach { bet ->
        val match = matchMap[bet.matchId]
        if (match != null) {
            val home = teamNameMap[match.homeTeam] ?: match.homeTeam
            val away = teamNameMap[match.awayTeam] ?: match.awayTeam
            val hs = match.homeScore
            val aws = match.awayScore

            val (outcome, payout) = if (hs != null && aws != null) {
                val actual = when {
                    hs > aws -> Prediction.HOME_WIN
                    hs < aws -> Prediction.AWAY_WIN
                    else -> Prediction.DRAW
                }
                if (actual == bet.prediction) {
                    "✓ WON" to (bet.amount * bet.odds)
                } else {
                    "✗ LOST" to 0.0
                }
            } else {
                "⏳ PENDING" to 0.0
            }

            val net = payout - bet.amount
            val netStr = if (net >= 0) "+$${String.format("%.2f", net)}" else "-$${String.format("%.2f", kotlin.math.abs(net))}"

            println("$home vs $away → You: ${formatPrediction(bet.prediction)} @ ${bet.odds} | $$${String.format("%.2f", bet.amount)} | $outcome | $netStr")
        }
    }
}

/* ============================================================================
   EXTENSION
   ============================================================================ */
private fun Knockout.toMatch(): Match = Match(
    matchId = this.matchId,
    round = this.round,
    date = this.date,
    homeTeam = this.homeTeam ?: this.homePlaceholder,
    awayTeam = this.awayTeam ?: this.awayPlaceholder,
    homeScore = this.homeScore,
    awayScore = this.awayScore,
    ground = this.ground,
    odds = this.odds
)