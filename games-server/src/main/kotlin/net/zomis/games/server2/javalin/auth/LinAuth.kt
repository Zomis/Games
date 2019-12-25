package net.zomis.games.server2.javalin.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.cUrlString
import com.github.kittinunf.fuel.core.extensions.jsonBody
import io.javalin.Javalin
import io.javalin.json.JavalinJackson
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.OAuthConfig
import net.zomis.games.server2.StartupEvent
import org.slf4j.LoggerFactory
import java.util.*

data class GithubAuthRequest(val clientId: String, val redirectUri: String, val code: String, val state: String?)

class LinAuth(val javalin: Javalin, val port: Int, val githubConfig: OAuthConfig) {

    private val logger = LoggerFactory.getLogger(LinAuth::class.java)

    fun register() {
        val secretProperties = Properties()
        val resource = this.javaClass.classLoader.getResourceAsStream("secrets.properties")
        logger.info(resource.toString())
        secretProperties.load(resource)
        logger.info("LinAuth starting")
        val mapper = ObjectMapper()
        mapper.registerModule(KotlinModule())

        JavalinJackson.configure(mapper)
        val app = javalin
            .enableCorsForOrigin("http://localhost:42637", "https://games.zomis.net")
            .apply {
                port(port)
                post("/auth/github") {
                    logger.info(it.toString())
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
                    val token = resultJson.get("access_token")
                    logger.info(token.asText())

                    it.result(mapper.writeValueAsString(resultJson))
                }
            }.start()
        logger.info("LinAuth started: $app")
    }

    private fun queryStringToJsonNode(mapper: ObjectMapper, queryString: String): ObjectNode {
        val node = mapper.createObjectNode()
        queryString.split("&")
            .map { val pair = it.split("="); Pair(pair[0], pair[1]) }
            .forEach { node.put(it.first, it.second) }
        return node
    }

}