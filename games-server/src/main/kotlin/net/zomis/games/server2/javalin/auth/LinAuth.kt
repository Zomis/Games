package net.zomis.games.server2.javalin.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import io.javalin.Context
import io.javalin.Javalin
import io.javalin.json.JavalinJackson
import net.zomis.games.server2.OAuthConfig
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.Properties

data class GithubAuthRequest(val clientId: String, val redirectUri: String, val code: String, val state: String?)

class LinAuth(val javalin: Javalin, val githubConfig: OAuthConfig, val googleConfig: OAuthConfig) {

    private val logger = LoggerFactory.getLogger(LinAuth::class.java)
    private val mapper = jacksonObjectMapper()

    fun register() {
        logger.info("LinAuth starting")

        JavalinJackson.configure(mapper)
        val app = javalin
            .apply {
                post("/auth/github") {
                    val params = it.bodyAsClass(GithubAuthRequest::class.java)
                    val result = Fuel.post("https://github.com/login/oauth/access_token", listOf(
                        Pair("client_id", githubConfig.clientId),
                        Pair("client_secret", githubConfig.clientSecret),
                        Pair("code", params.code),
                        Pair("redirect_uri", params.redirectUri),
                        Pair("state", params.state),
                        Pair("grant_type", "authorization_code")
                    )).responseString()

                    logger.info("Result: " + result.third.get())
                    val resultJson = queryStringToJsonNode(mapper, result.third.get())
                    it.result(mapper.writeValueAsString(resultJson))
                }
                post("/auth/google") {
                    googleHandler(it, googleConfig)
                }
                post("/auth/ping") {
                    it.result("auth pong")
                }
            }
        logger.info("LinAuth started: $app")
    }

    private fun googleHandler(context: Context, clientAndSecret: OAuthConfig) {
        val request: String = context.body()
        try {
            val tree = mapper.readTree(request)
            val result = Fuel.post("https://accounts.google.com/o/oauth2/token", listOf(
                "client_id" to clientAndSecret.clientId,
                "client_secret" to clientAndSecret.clientSecret,
                "code" to tree.get("code").asText(),
                "redirect_uri" to tree.get("redirectUri").asText(),
                "grant_type" to "authorization_code")
            ).responseString()
            logger.debug("Google Auth: {}", result.third)
            context.contentType("application/json").result(result.third.get())
        } catch (e: IOException) {
            context.status(500)
            logger.error("Authentication Failure for $request", e)
        }
    }

    private fun queryStringToJsonNode(mapper: ObjectMapper, queryString: String): ObjectNode {
        val node = mapper.createObjectNode()
        queryString.split("&")
            .map { val pair = it.split("="); Pair(pair[0], pair[1]) }
            .forEach { node.put(it.first, it.second) }
        return node
    }

}