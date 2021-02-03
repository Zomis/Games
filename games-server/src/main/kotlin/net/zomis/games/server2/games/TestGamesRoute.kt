package net.zomis.games.server2.games

import com.github.benmanes.caffeine.cache.Caffeine
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
    private val fakeHost = FakeClient().also {
        it.updateInfo("FakeClient", UUID.fromString("00000000-0000-0000-0000-000000000000"))
    }

    private val caffeine = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .refreshAfterWrite(1, TimeUnit.HOURS)
        .build { key: String -> createTestGame(key) }

    private fun createTestGame(gameType: String): ServerGame {
        val setup = ServerGames.setup(gameType) ?: throw IllegalArgumentException("No such gameType: $gameType")
        val gameConfig = setup.getDefaultConfig()
        val invite = inviteSystem.createInvite(gameType, inviteSystem.inviteIdGenerator(),
            InviteOptions(false, InviteTurnOrder.ORDERED, 0, gameConfig, false),
            fakeHost, emptyList()
        )
        val randomPlayerCount = invite.playerRange.random()
        for (i in 1 until randomPlayerCount) {
            invite.accepted.add(fakeHost)
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