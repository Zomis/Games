package net.zomis.server

interface Server {
    interface ServerGame {
        val invite: Unit
        val game: Unit
    }
    interface Games {
        /*
        * <id> (some is only chat, some is online players...)
        * - invite
        * - game
        * - chat
        * - replay
        * - online players
        *
        * Add/Remove client
        *
        * Player
        * - most recent finished games
        * - unfinished games -- where it's your turn
        * - unstarted games (inviting/invited)
        * - new messages
        *
        * Global
        * - unfinished games
        * - unstarted games
        * - recently finished games
        * - all game types: Name, picture, view, replay, AIs/opponents
        *
        * Decks/Presets
        * Campaigns
        */

        fun game(gameId: String): ServerGame
        fun unfinishedGamesForPlayer(playerId: String): ServerGame
        // Think about what serverless and server has in common
        // GameId provider, invite provider, AI Games provider,
        // chat provider, personal message chat provider, TV Screen provider...
    }
    val games: Games
//    val chats: Chats


}