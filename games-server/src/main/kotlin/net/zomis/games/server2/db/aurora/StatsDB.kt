package net.zomis.games.server2.db.aurora

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import klog.KLoggers
import net.zomis.games.server2.db.DBGameSummary
import net.zomis.games.server2.db.DBIntegration
import net.zomis.games.server2.db.GameState
import net.zomis.games.server2.db.SuperTable
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.invites.ClientList
import java.util.UUID
import kotlin.math.max

class StatsDB(private val dynamoDB: AmazonDynamoDB, private val superTable: SuperTable) {

    private val logger = KLoggers.logger(this)

    private val hikariConfig = HikariConfig("hikari.properties")
    val hikariDataSource = HikariDataSource(hikariConfig)

    private val tagStatsReady = "push-to-stats"

    private fun fetchNewlyFinishedGameIds(): List<String> {
        val scanResult = superTable.gsi.index
            .query("SK", tagStatsReady, RangeKeyCondition("Data").gt(Int.MIN_VALUE))
        return scanResult.map { it.getString("PK") }
    }

    fun saveNewlyFinishedInStats() {
        val existingGameIds = this.fetchExistingStatsGames().toSet()
        println("${existingGameIds.size} games in Postgres")
        val gameIds = this.fetchNewlyFinishedGameIds().also { println("${it.size} games in DynamoDB") }.map {
            SuperTable.Prefix.GAME.extract(it)
        }
        gameIds.minus(existingGameIds).asSequence().map { superTable.getGameSummary(SuperTable.Prefix.GAME.sk(it)) }
            .filterNotNull()
            .filter { it.gameState == GameState.PUBLIC.value }
            .forEach {
                this.insert(it)
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
        println("Type ${game.gameType} state ${game.gameState} LastAction ${game.timeLastAction} Started ${game.timeStarted}")
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
        return listOf("type/" + game.gameType to max(game.timeLastAction, game.timeStarted))
    }

}

fun main() {
    val games = GameSystem { ClientList(mutableSetOf()) }
    val amazonDynamoDB = DBIntegration(games).dynamoDB
    val db = StatsDB(amazonDynamoDB, SuperTable(amazonDynamoDB, games))
    db.saveNewlyFinishedInStats()
    db.hikariDataSource.close()
}
