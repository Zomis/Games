package net.zomis.games.server2.javalin.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import klog.KLoggers
import net.zomis.games.server2.OAuthConfig

data class GithubAuthRequest(val clientId: String, val redirectUri: String, val code: String, val state: String?)

class LinAuth(
    val githubConfig: OAuthConfig,
    val googleConfig: OAuthConfig,
    httpClientFactory: () -> HttpClient
) {

    private val logger = KLoggers.logger(this)
    private val mapper = jacksonObjectMapper()
    private val client = httpClientFactory.invoke()

    fun register(routing: Routing) {
        logger.info("LinAuth starting")
        routing.post("/auth/github") {
            val params = call.receive<GithubAuthRequest>()
            val result = client.submitForm(
                url = "https://github.com/login/oauth/access_token",
                formParameters = Parameters.build {
                    append("client_id", githubConfig.clientId)
                    append("client_secret", githubConfig.clientSecret)
                    append("code", params.code)
                    append("redirect_uri", params.redirectUri)
                    params.state?.also { append("state", params.state) }
                    append("grant_type", "authorization_code")
                }
            )

            logger.info { "Result: $result/${result.bodyAsText()}" }
            call.respond(result.body<JsonNode>())
        }
        routing.post("/auth/google") {
            val request = call.receive<ObjectNode>()
            googleHandler(this, request, googleConfig)
        }
        routing.get("/auth/ping") {
            call.respondText("auth pong")
        }
    }

    private suspend fun googleHandler(context: PipelineContext<Unit, ApplicationCall>, tree: ObjectNode, clientAndSecret: OAuthConfig) {
        try {
            val response = client.submitForm(
                url = "https://accounts.google.com/o/oauth2/token",
                formParameters = Parameters.build {
                    append("client_id", clientAndSecret.clientId)
                    append("client_secret", clientAndSecret.clientSecret)
                    append("code", tree.get("code").asText())
                    append("redirect_uri", tree.get("redirectUri").asText())
                    append("grant_type", "authorization_code")
                }
            )
            context.call.respond(response.body<ObjectNode>())
        } catch (e: Exception) {
            context.call.respond(HttpStatusCode.InternalServerError)
            logger.error(e) { "Authentication Failure for $tree" }
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