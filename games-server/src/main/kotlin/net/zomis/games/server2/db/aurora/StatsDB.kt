package net.zomis.games.server2.db.aurora

import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import klog.KLoggers
import net.zomis.games.server2.db.*
import java.time.Instant
import java.util.UUID

data class StatsPlayerInGame(val playerId: String, val result: Double, val resultPosition: Int)
data class StatsTag(val tagId: String, val tagParameter: Long)
data class StatsGameSummary(val gameId: String, val players: List<StatsPlayerInGame>, val tags: List<StatsTag>)

class StatsDB(private val superTable: SuperTable) {

    private val logger = KLoggers.logger(this)

    private val hikariConfig = HikariConfig("hikari.properties")
    val hikariDataSource = HikariDataSource(hikariConfig)

    private val tagStatsReady = "push-to-stats"

    private fun fetchNewlyFinishedGameIds(): List<String> {
        val scanResult = superTable.gsi.index
            .query("SK", tagStatsReady, RangeKeyCondition("Data").gt(Int.MIN_VALUE))
        return scanResult.map { it.getString("PK") }
    }

    fun query(players: List<String>, tags: List<String>): List<StatsGameSummary> {
        return hikariDataSource.connection.use {conn ->
            val tagJoins = tags.mapIndexed {index, tag ->
                val tableAlias = "tags_cond$index"
                "JOIN games_tags AS $tableAlias ON ($tableAlias.\"GameId\" = players_result.gameid AND $tableAlias.\"TagId\" = ?)"
            }.joinToString("\n")
            val playerJoins = players.mapIndexed {index, playerId ->
                val tableAlias = "players_cond$index"
                "JOIN games_players AS $tableAlias ON ($tableAlias.gameid = players_result.gameid AND $tableAlias.\"PlayerId\" = ?)"
            }.joinToString("\n")

            val sql = """
SELECT players_result.gameid,
players_result."PlayerIndex", players_result."PlayerId", players_result."ResultPosition", players_result."WinResult",
tags_result."TagId", tags_result."Parameter"
FROM games_players AS players_result
JOIN games_tags AS tags_result ON (tags_result."GameId" = players_result.gameid)
$playerJoins
$tagJoins
ORDER BY gameid ASC, "PlayerIndex" ASC
            """.trimIndent()
            val stmt = conn.prepareStatement(sql)
            players.forEachIndexed { index, playerId -> stmt.setObject(index + 1, UUID.fromString(playerId)) }
            tags.forEachIndexed { index, tagId -> stmt.setString(index + 1 + players.size, tagId) }

            logger.info { "Executing $sql with parameters $players and $tags" }

            val resultSet = stmt.executeQuery()
            val summaries = mutableListOf<StatsGameSummary>()
            while (resultSet.next()) {
                val player = StatsPlayerInGame(
                    playerId = resultSet.getString("PlayerId"),
                    result = resultSet.getDouble("WinResult"),
                    resultPosition = resultSet.getInt("ResultPosition")
                )
                val tag = StatsTag(resultSet.getString("TagId"), resultSet.getLong("Parameter"))
                val game = StatsGameSummary(resultSet.getString("gameid"), listOf(player), listOf(tag))
                summaries.add(game)
            }
            summaries.groupBy { it.gameId }.mapValues { game ->
                val playersInGame = game.value.flatMap { it.players }.distinct()
                val tagsInGame = game.value.flatMap { it.tags }.distinct()
                StatsGameSummary(game.key, playersInGame, tagsInGame)
            }.map { it.value }
        }
    }

    fun saveNewlyFinishedInStats() {
        val existingGameIds = this.fetchExistingStatsGames().toSet()
        existingGameIds.forEach { println(it) }
        println("${existingGameIds.size} games in Postgres")
        val gameIds = this.fetchNewlyFinishedGameIds().also { println("${it.size} games in DynamoDB") }.map {
            SuperTable.Prefix.GAME.extract(it)
        }
        for (gameId in gameIds.minus(existingGameIds)) {
            try {
                val summary = superTable.getGameSummary(SuperTable.Prefix.GAME.sk(gameId)) ?: continue
                if (summary.gameState == GameState.PUBLIC.value) {
                    insert(summary)
                }
            } catch (e: Exception) {
                logger.error(e) { "Unable to insert gameId $gameId to statistics" }
            }
        }
    }

    private fun fetchExistingStatsGames(): List<String> {
        return hikariDataSource.connection.use {conn ->
            val stmt = conn.prepareStatement("SELECT \"GameId\" FROM games_tags")
            val results = stmt.executeQuery()
            val games = mutableListOf<String>()
            while (results.next()) {
                games.add(results.getString(1))
            }
            games
        }
    }

    private fun insert(game: DBGameSummary) {
        if (game.playersInGame.any { it.results == null }) {
            logger.error { "Game not finished: ${game.gameId}" }
            return
        }
        println(game)
        println("Type ${game.gameType} state ${game.gameState} Started ${game.timeStarted}")
        hikariDataSource.connection.use {conn ->
            val tags = gameTags(game)
            tags.forEach {
                val stmt = conn.prepareStatement("INSERT INTO games_tags (\"GameId\", \"TagId\", \"Parameter\") VALUES (?, ?, ?)")
                stmt.setObject(1, UUID.fromString(game.gameId))
                stmt.setString(2, it.first)
                stmt.setLong(3, it.second)
                val affected = stmt.executeUpdate()
                if (affected <= 0) {
                    logger.error { "Unable to execute tagUpdate for $it on game ${game.gameId}" }
                }
            }

            game.playersInGame.forEach {pig ->
                val stmt = conn.prepareStatement(
                    "INSERT INTO games_players (gameid, \"PlayerIndex\", \"PlayerId\", \"ResultPosition\", \"WinResult\", \"Score\") " +
                        "VALUES (?, ?, ?, ?, ?, ?)")
                stmt.setObject(1, UUID.fromString(game.gameId))
                stmt.setInt(2, pig.playerIndex)
                stmt.setObject(3, UUID.fromString(pig.player!!.playerId))
                stmt.setInt(4, pig.results!!.resultPosition)
                stmt.setDouble(5, pig.results.result)
                stmt.setObject(6, null)
                val affected = stmt.executeUpdate()
                if (affected <= 0) {
                    logger.error { "Unable to execute statement for $game -> $pig" }
                }
            }
            superTable.logCapacity("Delete $tagStatsReady for ${game.gameId}", {it.deleteItemResult.consumedCapacity}, {
                val spec = DeleteItemSpec().withReturnConsumedCapacity(ReturnConsumedCapacity.INDEXES)
                    .withPrimaryKey(superTable.pk, SuperTable.Prefix.GAME.sk(game.gameId), superTable.sk, tagStatsReady)
                superTable.table.table.deleteItem(spec)
            })
        }
    }

    private fun gameTags(game: DBGameSummary): List<Pair<String, Long>> {
        return listOf("type/" + game.gameType to Instant.now().toEpochMilli())
    }

}

fun main() {
    val amazonDynamoDB = DBIntegration().dynamoDB
    val db = StatsDB(SuperTable(amazonDynamoDB))
    db.saveNewlyFinishedInStats()
    db.hikariDataSource.close()
}
