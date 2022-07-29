package net.zomis.games.ktor.plugins

import io.ktor.server.plugins.httpsredirect.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.http.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import net.zomis.games.server2.ServerConfig

fun Application.configureHTTP(config: ServerConfig) {
    install(DefaultHeaders) {
        header("X-Author", "Zomis")
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        //allowHeader("MyCustomHeader")

//        allowHost("localhost:8080", schemes = listOf("http", "https"))
//        config.clientURLs.split(';').forEach {
//            allowHost(it, schemes = listOf("http", "https"))
//        }
        anyHost()
    }
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }

}
