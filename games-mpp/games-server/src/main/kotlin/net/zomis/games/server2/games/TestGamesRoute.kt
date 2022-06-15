package net.zomis.games.server2.games

import com.github.benmanes.caffeine.cache.Caffeine
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.server2.ClientJsonMessage
import net.zomis.games.server2.MessageRouter
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.clients.FakeClient
import net.zomis.games.server2.invites.InviteOptions
import net.zomis.games.server2.invites.InviteSystem
import net.zomis.games.server2.invites.InviteTurnOrder
import java.util.*
import java.util.concurrent.TimeUnit

class TestGamesRoute(private val inviteSystem: InviteSystem) {

    val router = MessageRouter(this)
        .handler("game", this::game)

    private val maxPlayers = ServerGames.games.values.maxOf { GamesImpl.game(it).setup().playersCount.maxOrNull()!! }
    private val fakes = (0..maxPlayers).map {i ->
        FakeClient().also {
            it.updateInfo("Player$i", UUID.fromString("00000000-0000-0000-0000-${i.toStringLength(12)}"))
        }
    }

    private val caffeine = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .refreshAfterWrite(1, TimeUnit.HOURS)
        .build { key: String -> createTestGame(key) }

    private fun createTestGame(gameType: String): ServerGame {
        val setup = ServerGames.setup(gameType) ?: throw IllegalArgumentException("No such gameType: $gameType")
        val gameConfig = setup.configs()
        val invite = inviteSystem.createInvite(gameType, inviteSystem.inviteIdGenerator(),
            InviteOptions(false, InviteTurnOrder.ORDERED, 0, gameConfig, false),
            fakes[0], emptyList()
        )
        val randomPlayerCount = invite.playerRange.random()
        for (i in 1 until randomPlayerCount) {
            invite.accepted.add(fakes[i])
        }
        return invite.startCheck()
    }

    private fun game(message: ClientJsonMessage) {
        val key = message.data.get("gameType").asText()
        val game = caffeine.get(key)!!
        if (game.gameOver) {
            caffeine.invalidate(key)
            return game(message)
        }

        val playerCount = game.obj!!.playerCount
        (0 until playerCount).forEach {
            game.addPlayer(message.client).addAccess(it, ClientPlayerAccessType.WRITE)
        }
        game.sendGameStartedMessage(message.client)
    }

}

private fun Int.toStringLength(i: Int): String {
    var s = this.toString()
    while (s.length < i) s = "0$s"
    return s
}
