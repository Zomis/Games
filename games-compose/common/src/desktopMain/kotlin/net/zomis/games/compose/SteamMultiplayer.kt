package net.zomis.games.compose

import com.codedisaster.steamworks.*

/**
 * Steam Multiplayer Server + Client
 */
class SteamMultiplayer {

    fun init() {
//        val userId = SteamNativeHandle.getNativeHandle(user.steamID)
//        val byteBuffer = ByteBuffer.allocate(1024)
//        val key = user.getAuthSessionTicket(byteBuffer, intArrayOf(1024))
        // user.getAuthSessionTicket()
//        * 1. getAuthSessionTicket
//        * 2. send to server along with userId
//        * 3. Server: beginAuthSession
//        * 4. await ValidateAuthTicketResponse callback
        val user = createUser()
        println("User: ${user.steamID}")

        createNetworking()
        val s = "dsadsadsa"
        s.count { it in 'a'..'z' || it in 'A'..'Z' }
//        createNetworking().
//        user.getAuthSessionTicket()
    }

    private fun createNetworking(): SteamNetworking {
        return SteamNetworking(object : SteamNetworkingCallback {
            override fun onP2PSessionConnectFail(
                steamIDRemote: SteamID,
                sessionError: SteamNetworking.P2PSessionError
            ) {
                println("onP2PSessionConnectFail $steamIDRemote $sessionError")
                TODO("Not yet implemented")
            }

            override fun onP2PSessionRequest(steamIDRemote: SteamID) {
                println("onP2PSessionRequest $steamIDRemote")
            }
        })
    }

    private fun createUser(): SteamUser {
        return SteamUser(object : SteamUserCallback {
            override fun onAuthSessionTicket(authTicket: SteamAuthTicket, result: SteamResult) {
                println("onAuthSessionTicket $authTicket $result")
            }

            override fun onValidateAuthTicket(
                steamID: SteamID,
                authSessionResponse: SteamAuth.AuthSessionResponse,
                ownerSteamID: SteamID
            ) {
                println("onValidateAuthTicket $steamID $authSessionResponse $ownerSteamID")
            }

            override fun onMicroTxnAuthorization(appID: Int, orderID: Long, authorized: Boolean) {
                println("onMicroTxnAuthorization $appID $orderID $authorized")
            }

            override fun onEncryptedAppTicket(result: SteamResult) {
                println("onEncryptedAppTicket $result")
            }
        })
    }

}