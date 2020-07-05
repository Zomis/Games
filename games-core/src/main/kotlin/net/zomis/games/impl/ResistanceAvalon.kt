package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.cards.CardZone
import net.zomis.games.common.next
import net.zomis.games.dsl.ActionChoicesNextScope
import net.zomis.games.dsl.GameCreator
import net.zomis.games.dsl.withIds
import kotlin.random.Random

class ResistanceAvalonMission(val playerCount: Int, val missionNumber: Int, val teamSize: Int, var result: Boolean? = null) {
    var failsGotten: Int? = null
    val failsNeeded = if (playerCount >= 7 && missionNumber == 4) 2 else 1
    fun markAsCompleted(fails: Int) {
        this.result = fails < failsNeeded
        this.failsGotten = fails
    }

    val completed: Boolean get() = result != null
}

enum class ResistanceAvalonPlayerCharacter(val good: Boolean) {
    MERLIN(true),
    ASSASSIN(false),
    MORDRED(false),
    PERCIVAL(true),
    OBERON(false),
    MORGANA(false),
    MINION_OF_MORDRED(false),
    LOYAL_SERVANT_OF_KING_ARTHUR(true),
    ;

    fun seesThumbs(): Set<ResistanceAvalonPlayerCharacter> = when (this) {
        MERLIN -> values().filter { !it.good && it != MORDRED }.toSet()
        ASSASSIN, MORDRED, MORGANA, MINION_OF_MORDRED -> values().filter { !it.good && it != OBERON }.toSet()
        PERCIVAL -> setOf(MERLIN, MORGANA)
        else -> setOf()
    }
}

class ResistanceAvalonPlayer(val index: Int) {
    var vote: Boolean? = null
    var missionSuccess: Boolean? = null
    var character: ResistanceAvalonPlayerCharacter? = null
    var ladyOfTheLakeImmunity: Boolean = false
    var knownThumbs: Set<ResistanceAvalonPlayer>? = null
}
data class ResistanceAvalonConfig(
    val useMerlin: Boolean = true,
    val usePercival: Boolean = false,
    val useMordred: Boolean = false,
    val useOberon: Boolean = false,
    val useMorgana: Boolean = false,
    val chooseMission: Boolean = false,
    val ladyOfTheLake: Boolean = false
) {
    fun characters(playerCount: Int): List<ResistanceAvalonPlayerCharacter> {
        val list = mutableListOf<ResistanceAvalonPlayerCharacter>()
        if (useMerlin) {
            list.add(ResistanceAvalonPlayerCharacter.MERLIN)
            list.add(ResistanceAvalonPlayerCharacter.ASSASSIN)
        }
        if (useMordred) list.add(ResistanceAvalonPlayerCharacter.MORDRED)
        if (useMorgana) list.add(ResistanceAvalonPlayerCharacter.MORGANA)
        if (useOberon) list.add(ResistanceAvalonPlayerCharacter.OBERON)
        if (usePercival) list.add(ResistanceAvalonPlayerCharacter.PERCIVAL)

        val wantedEvil = when (playerCount) {
            in 5..6 -> 2
            in 7..9 -> 3
            10 -> 4
            else -> throw UnsupportedOperationException("Unexpected playerCount: $playerCount")
        }
        val wantedGood = playerCount - wantedEvil
        val actualEvil = list.count { !it.good }
        if (actualEvil > wantedEvil) {
            throw UnsupportedOperationException("Expected $wantedEvil evil but found $actualEvil")
        }
        while (list.count { it.good } < wantedGood) list.add(ResistanceAvalonPlayerCharacter.LOYAL_SERVANT_OF_KING_ARTHUR)
        while (list.count { !it.good } < wantedEvil) list.add(ResistanceAvalonPlayerCharacter.MINION_OF_MORDRED)
        return list
    }
}
class ResistanceAvalon(val config: ResistanceAvalonConfig, val playerCount: Int) {
    val charactersUsed = config.characters(playerCount)
    val players = (0 until playerCount).map { ResistanceAvalonPlayer(it) }
    val missions = (1..5).map { missionNumber -> ResistanceAvalonMission(playerCount = playerCount, missionNumber = missionNumber,
        teamSize = when (playerCount) {
            5 -> arrayOf(2, 3, 2, 3, 3)
            6 -> arrayOf(2, 3, 4, 3, 4)
            7 -> arrayOf(2, 3, 3, 4, 4)
            in 8..10 -> arrayOf(3, 4, 4, 5, 5)
            else -> throw IllegalArgumentException("Invalid number of players $playerCount")
        }.let { it[missionNumber - 1] })
    }
    var ladyOfTheLakePlayer: ResistanceAvalonPlayer? = null
    var leaderIndex: Int = 0

    var voteTeam: ResistanceAvalonTeamChoice? = null
    var activeMission: ResistanceAvalonMission? = null
    var rejectedTeams: Int = 0
}

class ResistanceAvalonTeamChoice(val mission: ResistanceAvalonMission, val team: List<ResistanceAvalonPlayer>)
data class ResistanceAvalonTeamChoiceSerialized(val mission: Int, val team: List<Int>)

object ResistanceAvalonGame {

    val factory = GameCreator(ResistanceAvalon::class)
    val chooseTeam = factory.action("teamChoice", ResistanceAvalonTeamChoice::class).serializer(ResistanceAvalonTeamChoiceSerialized::class) {
        ResistanceAvalonTeamChoiceSerialized(it.mission.missionNumber, it.team.map { pl -> pl.index })
    }
    val vote = factory.action("vote", Boolean::class)
    val performMission = factory.action("performMission", Boolean::class)

    val assassinate = factory.action("assassinate", ResistanceAvalonPlayer::class).serializer(Int::class) { it.index }
    val useLadyOfTheLake = factory.action("useLadyOfTheLake", ResistanceAvalonPlayer::class).serializer(Int::class) { it.index }

    val game = factory.game("Avalon") {
        setup(ResistanceAvalonConfig::class) {
            players(5..10)
            defaultConfig { ResistanceAvalonConfig() }
            init {
                if (config.ladyOfTheLake) {
                    throw UnsupportedOperationException("Lady of the lake not yet supported")
                }
                ResistanceAvalon(config, playerCount)
            }
        }
        rules {
            gameStart {
                val characters = CardZone(game.charactersUsed.toMutableList())
                characters.random(replayable, game.playerCount, "characters") { it.name }.toList()
                    .forEachIndexed { index, card -> game.players[index].character = card.card }

                game.players.forEach {
                    it.knownThumbs = it.character!!.seesThumbs().mapNotNull { ch -> game.players.find { pl -> pl.character == ch } }.toSet()
                }

                val leader = replayable.int("leader") { 0 } // TODO: Random.Default.nextInt(game.playerCount) }
                game.leaderIndex = leader
            }

            view("characters") { game.charactersUsed.map { it.name }.distinct() }
            view("players") {
                val myself = if (viewer != null) game.players[viewer!!] else null
                val allVoted = game.players.all { it.vote != null }
                game.players.map {
                    mapOf(
                        "thumb" to myself?.knownThumbs?.contains(it),
                        "character" to if (myself?.index == it.index || true) it.character?.name else null,
                        "vote" to it.vote.takeIf { allVoted },
                        "leader" to (it.index == game.leaderIndex),
                        "inTeam" to game.voteTeam?.team?.contains(it)
                    )
                }
            }
            view("votingTeam") {
                mapOf(
                    "missionNumber" to game.voteTeam?.mission?.missionNumber,
                    "team" to game.voteTeam?.team?.map { it.index }
                )
            }
            view("currentMission") {
                game.activeMission?.missionNumber
            }
            view("missions") {
                game.missions.map {
                    mapOf(
                        "missionNumber" to it.missionNumber,
                        "teamSize" to it.teamSize,
                        "failsNeeded" to it.failsNeeded,
                        "failsGotten" to it.failsGotten,
                        "result" to it.result
                    )
                }
            }
            view("rejectedTeams") { game.rejectedTeams }

            action(chooseTeam) {
                precondition { game.voteTeam == null && game.activeMission == null }
                precondition { game.leaderIndex == playerIndex }
                precondition { game.missions.any { !it.completed } }
                choose {
                    optionsWithIds({
                        val remainingMissions = game.missions.filter { !it.completed }
                        (if (game.config.chooseMission) remainingMissions else listOf(remainingMissions.first()))
                            .withIds { it.missionNumber.toString() }
                    }) {mission ->
                        fun rec(scope: ActionChoicesNextScope<ResistanceAvalon, ResistanceAvalonTeamChoice>,
                            chosen: List<ResistanceAvalonPlayer>,
                            required: Int
                        ) {
                            if (required <= 0) {
                                scope.parameter(ResistanceAvalonTeamChoice(mission, chosen))
                                return
                            }
                            scope.optionsWithIds({ (game.players - chosen).withIds { it.index.toString() } }) { playerChoice ->
                                rec(this, chosen + playerChoice, required - 1)
                            }
                        }
                        rec(this, listOf(), mission.teamSize)
                    }
                }
                effect {
                    game.players.forEach { it.vote = null }
                    game.voteTeam = action.parameter
                    log {
                        val team = action.team.map { player(it.index) }.joinToString(", ")
                        "$player chose mission ${action.mission.missionNumber} and team $team"
                    }
                }
            }

            action(vote) {
                precondition { game.voteTeam != null && game.activeMission == null }
                precondition { game.players[playerIndex].vote == null }
                options { listOf(false, true) }
                effect {
                    game.players[playerIndex].vote = action.parameter
                    log { "$player has voted" }
                }
                after {
                    if (game.players.all { it.vote != null }) {
                        val accepted = game.players.count { it.vote == true }
                        val rejected = game.players.count { it.vote == false }

                        game.players.forEach { pl ->
                            log { "${player(pl.index)} voted ${pl.vote}" }
                        }
                        val approved = accepted > rejected
                        log { "The mission was ${if (approved) "approved" else "rejected"}" }
                        if (approved) {
                            game.activeMission = game.voteTeam!!.mission
                            game.rejectedTeams = 0
                        } else {
                            game.rejectedTeams++
                            game.leaderIndex = game.leaderIndex.next(game.playerCount)
                            game.voteTeam = null
                        }
                    }
                }
            }

            action(performMission) {
                precondition { game.activeMission != null && game.voteTeam != null }
                precondition { game.players[playerIndex].missionSuccess == null }
                precondition { game.voteTeam?.team?.any { it.index == playerIndex } ?: false }
                options { listOf(false, true) }
                effect {
                    game.players[playerIndex].missionSuccess = action.parameter
                }
                after {
                    val team = game.voteTeam!!.team
                    if (team.all { it.missionSuccess != null }) {
                        val fails = team.count { it.missionSuccess == false }
                        log { "Mission completed with $fails fails" }
                        game.activeMission!!.markAsCompleted(fails)
                        game.leaderIndex = game.leaderIndex.next(game.playerCount)
                        game.activeMission = null
                        game.voteTeam = null
                        team.forEach { it.missionSuccess = null }
                    }
                }
            }

            allActions.after {
                val failedMissions = game.missions.count { it.result == false }
                val successMissions = game.missions.count { it.result == true }
                val goodWins: Boolean? = when {
                    failedMissions >= 3 -> false
                    successMissions >= 3 -> true
                    game.rejectedTeams >= 5 -> false
                    else -> null
                }
                if (goodWins != null) {
                    val winners = game.players.filter { it.character!!.good == goodWins }
                    winners.forEach {
                        eliminations.result(it.index, WinResult.WIN)
                    }
                    eliminations.eliminateRemaining(WinResult.LOSS)
                }
            }
        }
    }

}